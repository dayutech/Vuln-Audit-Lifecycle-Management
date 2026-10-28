#!/bin/bash
# 把前端构建产物同步到 nginx 站点根目录（/home/leo 为 750，www-data 无法穿越，
# 因此静态文件放在 /var/www/vuln-audit）。前端重新构建后执行本脚本即可生效。
set -e
SRC="$HOME/apps/vuln-audit/frontend/dist/"
DST="/var/www/vuln-audit/"
sudo rsync -a --delete --chown=root:root --chmod=D755,F644 "$SRC" "$DST"
sudo systemctl reload nginx
echo "已同步 $(find "$DST" -type f | wc -l) 个文件到 $DST 并重载 nginx"
