#!/usr/bin/env python3
"""t188 部署辅助：通过 paramiko 在 192.168.188.226 上执行命令 / 上传文件。

用法:
  python ssh_run.py exec "<shell 命令>"          # 执行命令并返回输出
  python ssh_run.py sudo "<shell 命令>"          # 以 sudo 执行（口令从环境变量 LX_SUDO_PASS 读）
  python ssh_run.py put <本地文件> <远端路径>     # SFTP 上传
"""
import os
import sys

import paramiko

HOST = os.environ.get("LX_HOST", "192.168.188.226")
USER = os.environ.get("LX_USER", "leo")
PASSWORD = os.environ.get("LX_PASS")  # 不设默认值：口令绝不进仓库


def connect() -> paramiko.SSHClient:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    if not PASSWORD:
        raise SystemExit("请先设置环境变量 LX_PASS（SSH 口令），仓库内不保存任何口令")
    client.connect(HOST, username=USER, password=PASSWORD, timeout=20, banner_timeout=30, auth_timeout=30)
    return client


def run(client: paramiko.SSHClient, command: str) -> int:
    _, stdout, stderr = client.exec_command(command, timeout=600)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out:
        sys.stdout.write(out)
    if err:
        sys.stderr.write(err)
    return code


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    mode, payload = sys.argv[1], sys.argv[2]
    with connect() as client:
        if mode == "exec":
            return run(client, payload)
        if mode == "sudo":
            sudo_pass = os.environ.get("LX_SUDO_PASS", PASSWORD)
            # 用 -S 从 stdin 读口令；命令本身用单引号包住，避免被 shell 展开
            return run(client, f"echo {sudo_pass!r} | sudo -S bash -lc {payload!r}")
        if mode == "put":
            if len(sys.argv) < 4:
                print("用法: put <本地文件> <远端路径>")
                return 2
            remote = sys.argv[3]
            sftp = client.open_sftp()
            sftp.put(payload, remote)
            sftp.close()
            print(f"uploaded {payload} -> {remote}")
            return 0
    print(f"未知模式: {mode}")
    return 2


if __name__ == "__main__":
    sys.exit(main())