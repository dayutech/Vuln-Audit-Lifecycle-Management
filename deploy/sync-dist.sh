#!/bin/bash
# 把前端构建产物同步到 nginx 站点根目录（/home/leo 为 750，www-data 无法穿越，
# 因此静态文件放在 /var/www/vuln-audit）。前端重新构建后执行本脚本即可生效。
#
# 非交互执行（CI / ssh 远程）时通过环境变量传入口令：
#   SUDO_PASS='xxx' bash sync-dist.sh
set -e

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$APP_DIR/frontend/dist/"
DST="/var/www/vuln-audit/"

# 统一 sudo 入口：有 SUDO_PASS 时走 -S，否则依赖交互输入或已缓存的凭据
run_sudo() {
    if [ -n "${SUDO_PASS:-}" ]; then
        echo "$SUDO_PASS" | sudo -S -p '' "$@"
    else
        sudo "$@"
    fi
}

if [ ! -d "$SRC" ]; then
    echo "未找到构建产物: $SRC（请先执行 npm run build）" >&2
    exit 1
fi

run_sudo install -d -o root -g root -m 755 "$DST"
run_sudo rsync -a --delete --chown=root:root --chmod=D755,F644 "$SRC" "$DST"
run_sudo systemctl reload nginx

echo "已同步 $(find "$DST" -type f | wc -l) 个文件到 $DST 并重载 nginx"
