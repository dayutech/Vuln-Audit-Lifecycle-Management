#!/usr/bin/env bash
# t192 部署：替换后端 jar + 同步前端 dist（nginx/环境变量沿用上一轮，不动）
# 前置：/tmp/vuln-audit-backend.jar、/tmp/dist.tar.gz 已上传；SUDO_PASS 已 export
set -euo pipefail

APP="$HOME/apps/vuln-audit"
STAMP=$(date +%F-%H%M)
JAR="$APP/backend/target/vuln-audit-backend-1.0.0-SNAPSHOT.jar"
SUDO_PASS="${SUDO_PASS:?请先 export SUDO_PASS=...}"

sudo_run() { echo "$SUDO_PASS" | sudo -S "$@"; }

echo "== 1/4 前端静态站点 =="
rm -rf /tmp/distnew && mkdir -p /tmp/distnew
tar -xzf /tmp/dist.tar.gz -C /tmp/distnew
sudo_run rsync -a --delete --chown=root:root --chmod=D755,F644 /tmp/distnew/ /var/www/vuln-audit/
ls -1 /var/www/vuln-audit/static

echo "== 2/4 替换后端 jar =="
cp "$JAR" "$JAR.bak-t192-$STAMP"
mv /tmp/vuln-audit-backend.jar "$JAR"
ls -l "$JAR"

echo "== 3/4 重启后端 =="
systemctl --user restart vuln-audit-backend
for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8089/api/v1/auth/login || echo 000)
  if [ "$code" = "405" ]; then echo "ready after $i probes"; break; fi
  sleep 2
  if [ "$i" = "45" ]; then
    echo "NOT READY (last=$code)"
    journalctl --user -u vuln-audit-backend -n 40 --no-pager || true
    exit 1
  fi
done

echo "== 4/4 状态 =="
systemctl --user is-active vuln-audit-backend
ss -lntp 2>/dev/null | grep 8088 || true
echo "done: $STAMP"