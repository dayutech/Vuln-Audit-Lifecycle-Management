#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""部署初始化：清理畸型示例数据并种入最小可用基础数据（团队 + 示例资产）。"""
import json
import os
import subprocess
import urllib.request

BASE = "http://127.0.0.1:8088/api/v1"
DB_PWD = "VulnApp_2026#Db9x"


def sql(statement):
    env = dict(os.environ, MYSQL_PWD=DB_PWD)
    return subprocess.run(
        ["mariadb", "-uvulnapp", "vuln_audit", "-N", "-e", statement],
        env=env, capture_output=True, text=True, check=True,
    ).stdout.strip()


def call(method, path, payload=None, token=None):
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    print("== cleanup previous garbled rows ==")
    for table in ("asset_versions", "assets", "teams"):
        sql(f"DELETE FROM {table}")
    print("assets after cleanup:", sql("SELECT COUNT(*) FROM assets"))

    login = call("POST", "/auth/login", {"username": "admin", "password": "Admin@123"})
    token = login["data"]["token"]
    me = call("GET", "/auth/me", token=token)["data"]
    print("login ok:", me["username"], me["fullName"])

    team = call("POST", "/teams", {
        "name": "漏洞挖掘一组",
        "description": "默认团队（部署初始化，可删除）",
        "leaderId": me["id"],
        "createdBy": me["id"],
    }, token)["data"]
    print("team:", team["name"], team["id"])

    assets = [
        ("示例-WEB 门户系统", "Web应用", "客户委托", "P1"),
        ("示例-IoT 摄像头固件", "IoT设备", "自主发现", "P2"),
        ("示例-桌面客户端", "C/S客户端", "客户委托", "P3"),
    ]
    for name, ttype, tsource, prio in assets:
        created = call("POST", "/assets", {
            "name": name,
            "targetType": ttype,
            "targetSource": tsource,
            "description": "部署初始化示例资产，可删除",
            "priority": prio,
            "status": "active",
            "teamId": team["id"],
        }, token)["data"]
        print("  asset:", created["code"], created["name"])

    opts = call("GET", "/assets/options", token=token)["data"]
    print("targetTypes:", [o["value"] for o in opts["targetTypes"]])
    print("targetSources:", [o["value"] for o in opts["targetSources"]])
    print("vuln types:", sql("SELECT GROUP_CONCAT(name ORDER BY display_order SEPARATOR ',') FROM vulnerability_types")[:120])


if __name__ == "__main__":
    main()
