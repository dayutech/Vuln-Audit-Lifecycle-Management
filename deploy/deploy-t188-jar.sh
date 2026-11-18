#!/usr/bin/env bash
# t188 二次收口：仅替换后端 jar 并重启（/tmp/vuln-audit-backend.jar 需已上传）
set -euo pipefail

APP="$HOME/apps/vuln-audit"
JAR="$APP/backend/target/vuln-audit-backend-1.0.0-SNAPSHOT.jar"
STAMP=$(date +%F-%H%M)

cp "$JAR" "$JAR.bak-t188b-$STAMP"
mv /tmp/vuln-audit-backend.jar "$JAR"
ls -l "$JAR"

systemctl --user restart vuln-audit-backend

for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8089/api/v1/auth/login || echo 000)
  if [ "$code" = "405" ]; then
    echo "ready after $i probes"
    break
  fi
  sleep 2
  if [ "$i" = "40" ]; then
    echo "NOT READY (last=$code)"
    systemctl --user status vuln-audit-backend --no-pager -n 30 || true
    exit 1
  fi
done

systemctl --user is-active vuln-audit-backend
ss -lntp 2>/dev/null | grep 8088 || true