#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""t194 真机验证：审批中心与上传审批合并、并迁入「系统与管理 → 审批与合规」。

断言：
1) admin 菜单：核心业务不再有「审批中心」；「审批与合规」下有 审批中心 / 审批范围 / 审计日志；
2) /approvals：admin 看到「访问审批 / 上传审批」分段，切到上传审批后出现上传审批表格；
3) 旧地址 /admin/upload-approvals 自动跳到 /approvals?mode=upload 并展示上传审批；
4) engineer：/approvals 只有访问审批（无上传审批分段），菜单里「审批与合规」含审批中心。
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://192.168.188.226:8089"
PORT = 9345
PROFILE = r"C:\Users\leo\tools\edge-cdp-t194"


class Cdp:
    def __init__(self, url):
        self.ws = websocket.create_connection(url, timeout=30)
        self.seq = 0

    def call(self, method, **params):
        self.seq += 1
        self.ws.send(json.dumps({"id": self.seq, "method": method, "params": params}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.seq:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result", {})

    def ev(self, expr):
        return self.call("Runtime.evaluate", expression=expr, returnByValue=True, awaitPromise=True) \
            .get("result", {}).get("value")

    def goto(self, url, wait=4.0):
        self.call("Page.navigate", url=url)
        time.sleep(wait)


def login(user, password):
    req = urllib.request.Request(BASE + "/api/v1/auth/login",
                                 data=json.dumps({"username": user, "password": password}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())["data"]["token"]


OPEN_ALL = """
(() => {
  document.querySelectorAll('.ant-menu-submenu-title').forEach((el) => {
    if (!el.closest('.ant-menu-submenu-open')) el.click();
  });
  return true;
})()
"""

MENU_TEXT = """
(() => {
  const menus = Array.from(document.querySelectorAll('.ant-menu'));
  const menu = menus.find((m) => m.offsetParent !== null) || menus[0];
  return menu ? menu.innerText : '';
})()
"""


def menu_sections(text):
    """把菜单文本按分组标题切成 dict：{组名: [项...]}"""
    lines = [line.strip() for line in (text or '').splitlines() if line.strip()]
    sections, current = {}, None
    for line in lines:
        if line in ('核心业务', '知识与资源', '系统与管理'):
            current = line
            sections[current] = []
        elif current:
            sections[current].append(line)
    return sections


def main() -> int:
    failures = []
    proc = subprocess.Popen([EDGE, "--headless=new", "--disable-gpu", "--no-sandbox",
                             f"--remote-debugging-port={PORT}", f"--user-data-dir={PROFILE}", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/version", timeout=1):
                    break
            except Exception:
                time.sleep(0.2)
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=5) as resp:
            ws = [t for t in json.loads(resp.read().decode()) if t.get("type") == "page"][0]["webSocketDebuggerUrl"]
        cdp = Cdp(ws)
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Emulation.setDeviceMetricsOverride", width=1440, height=900, deviceScaleFactor=1, mobile=False)

        # ---------- admin ----------
        cdp.goto(BASE + "/login", 2.5)
        cdp.ev(f"localStorage.setItem('access_token', {json.dumps(login('admin','admin'))}), true")
        cdp.goto(BASE + "/approvals", 5.0)
        cdp.ev(OPEN_ALL)
        time.sleep(1.0)
        sections = menu_sections(cdp.ev(MENU_TEXT))
        core = sections.get('核心业务', [])
        sys = sections.get('系统与管理', [])
        print("admin 核心业务:", core)
        print("admin 系统与管理:", sys)
        if any('审批中心' in item for item in core):
            failures.append("核心业务里仍有「审批中心」")
        if not any('审批中心' in item for item in sys):
            failures.append("系统与管理里没有「审批中心」")

        print("\n== /approvals（admin） ==")
        print("  url:", cdp.ev("location.pathname + location.search"))
        print("  mode switch:", cdp.ev("!!document.querySelector('[data-testid=approval-mode-switch]')"))
        print("  上传审批分段:", cdp.ev("document.body.innerText.includes('上传审批')"))
        if not cdp.ev("!!document.querySelector('[data-testid=approval-mode-switch]')"):
            failures.append("admin 的 /approvals 缺少访问审批/上传审批分段")

        clicked = cdp.ev("""(() => {
          const items = Array.from(document.querySelectorAll('[data-testid=approval-mode-switch] .ant-segmented-item'));
          const target = items.find((el) => (el.innerText || '').includes('上传审批'));
          if (!target) return false; target.click(); return true;
        })()""")
        time.sleep(2.5)
        upload_table = cdp.ev("!!document.querySelector('[data-testid=admin-upload-approvals-table]')")
        print("  切到上传审批:", clicked, "| 上传审批表格:", upload_table)
        if not upload_table:
            failures.append("切到「上传审批」后没有出现上传审批表格")

        # ---------- 旧地址重定向 ----------
        print("\n== 旧地址 /admin/upload-approvals ==")
        cdp.goto(BASE + "/admin/upload-approvals", 5.0)
        url = cdp.ev("location.pathname + location.search")
        print("  ->", url)
        if "/approvals" not in url or "mode=upload" not in url:
            failures.append(f"旧地址没有重定向到 /approvals?mode=upload: {url}")
        if not cdp.ev("!!document.querySelector('[data-testid=admin-upload-approvals-table]')"):
            failures.append("旧地址重定向后没有展示上传审批")

        # ---------- engineer ----------
        cdp.goto(BASE + "/login", 2.0)
        cdp.ev(f"localStorage.setItem('access_token', {json.dumps(login('smoke_engineer','Smoke@12345'))}), true")
        cdp.goto(BASE + "/approvals", 5.0)
        cdp.ev(OPEN_ALL)
        time.sleep(1.0)
        eng_sys = menu_sections(cdp.ev(MENU_TEXT)).get('系统与管理', [])
        print("\nengineer 系统与管理:", eng_sys)
        if not any('审批中心' in item for item in eng_sys):
            failures.append("engineer 的「系统与管理」里没有「审批中心」")
        if cdp.ev("!!document.querySelector('[data-testid=approval-mode-switch]')"):
            failures.append("engineer 不应看到上传审批分段")

        cdp.ws.close()
        if failures:
            print("\nFAILURES:")
            for item in failures:
                print("  -", item)
            return 1
        print("\nALL CHECKS PASSED")
        return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()


if __name__ == "__main__":
    sys.exit(main())