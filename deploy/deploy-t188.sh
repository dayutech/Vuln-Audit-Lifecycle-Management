#!/usr/bin/env bash
# t188 安全加固部署脚本（在 192.168.188.226 上以 leo 身份执行）
# 用法：bash /tmp/deploy_t188.sh
# 前置：/tmp/vuln-audit-backend.jar、/tmp/dist.tar.gz、/tmp/nginx-vuln-audit.conf、
#       /tmp/vuln-audit-security-headers.conf 已上传
set -euo pipefail

APP="$HOME/apps/vuln-audit"
STAMP=$(date +%F-%H%M)
SUDO_PASS="${SUDO_PASS:?请先 export SUDO_PASS=...（sudo 口令，绝不写进仓库）}"
JAR_SRC=/tmp/vuln-audit-backend.jar
JAR_DST="$APP/backend/target/vuln-audit-backend-1.0.0-SNAPSHOT.jar"

sudo_run() { echo "$SUDO_PASS" | sudo -S "$@"; }

echo "== 1/7 backend.env：确保 JWT_SECRET 与 SERVER_ADDRESS =="
cp "$APP/deploy/backend.env" "$APP/deploy/backend.env.bak-t188-$STAMP"
if ! grep -q '^JWT_SECRET=' "$APP/deploy/backend.env"; then
  sed -i '/^JWT_SECRET=/d;/^SERVER_ADDRESS=/d' "$APP/deploy/backend.env"
  printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48 | tr -d '\n')" >> "$APP/deploy/backend.env"
  echo "  已生成新的 JWT_SECRET"
else
  echo "  JWT_SECRET 已存在，保持不变"
fi
grep -q '^SERVER_ADDRESS=' "$APP/deploy/backend.env" || echo 'SERVER_ADDRESS=127.0.0.1' >> "$APP/deploy/backend.env"
chmod 600 "$APP/deploy/backend.env"
echo "  环境变量（脱敏）："
sed -E 's/(SECRET|PASSWORD|KEY)=.*/\1=***/' "$APP/deploy/backend.env" | sed 's/^/    /'

echo "== 2/7 替换后端 jar =="
cp "$JAR_DST" "$APP/backend/target/vuln-audit-backend-1.0.0-SNAPSHOT.jar.bak-t188-$STAMP"
mv "$JAR_SRC" "$JAR_DST"
ls -l "$JAR_DST"

echo "== 3/7 前端静态站点（同批上线）=="
rm -rf /tmp/distnew && mkdir -p /tmp/distnew
tar -xzf /tmp/dist.tar.gz -C /tmp/distnew
sudo_run mkdir -p /var/www/vuln-audit
sudo_run rsync -a --delete --chown=root:root --chmod=D755,F644 /tmp/distnew/ /var/www/vuln-audit/
ls -1 /var/www/vuln-audit/static | sed 's/^/    /'

echo "== 4/7 nginx：安全头 snippet + 站点配置 =="
sudo_run cp /etc/nginx/sites-available/vuln-audit "/tmp/nginx-site.bak-t188-$STAMP"
sudo_run mkdir -p /etc/nginx/snippets
sudo_run cp /tmp/vuln-audit-security-headers.conf /etc/nginx/snippets/vuln-audit-security-headers.conf
sudo_run cp /tmp/nginx-vuln-audit.conf /etc/nginx/sites-available/vuln-audit
if sudo_run nginx -t; then
  sudo_run systemctl reload nginx
  echo "  nginx 配置校验通过并已 reload"
else
  echo "  !! nginx 配置校验失败，回滚站点配置"
  sudo_run cp "/tmp/nginx-site.bak-t188-$STAMP" /etc/nginx/sites-available/vuln-audit
  exit 1
fi

echo "== 5/7 重启后端 =="
systemctl --user restart vuln-audit-backend

echo "== 6/7 等待就绪（登录端点 GET 应返回 405，而不是 502）=="
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8089/api/v1/auth/login || true)
  if [ "$code" = "405" ]; then echo "  就绪（第 ${i} 次探测，HTTP 405）"; break; fi
  sleep 2
  if [ "$i" = "40" ]; then
    echo "  !! 后端未就绪，最后状态码=$code"
    systemctl --user status vuln-audit-backend --no-pager -n 30 || true
    exit 1
  fi
done

echo "== 7/7 服务状态与监听 =="
systemctl --user is-active vuln-audit-backend
ss -lntp 2>/dev/null | grep -E ':8088|:8089' || true
echo "部署完成：$STAMP"