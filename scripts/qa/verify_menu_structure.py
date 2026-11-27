#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""t191 侧边栏菜单重组 + 顶栏铃铛的真机验证（headless Edge + CDP）。

覆盖：
- 管理员 / 组长（project_leader）/ 工程师 三种角色的可见项矩阵（逐子菜单比对）；
- 「系统与管理」= 4 个子菜单 + 系统消息；「知识与资源」只剩 3 项；
- 进入子页面时对应子菜单自动展开、叶子高亮；
- 顶栏铃铛角标与侧栏「系统消息」角标联动（派发未读数事件后两处同时变化）。

用法：python scripts/qa/verify_menu_structure.py
账号来自冒烟预置（smoke_leader / smoke_engineer，口令 Smoke@12345）。
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://192.168.188.226:8089"
PORT = 9340
PROFILE = r"C:\Users\leo\tools\edge-cdp-menu"

ACCOUNTS = [
    ("admin", "admin", "admin"),
    ("smoke_leader", "Smoke@12345", "leader"),
    ("smoke_engineer", "Smoke@12345", "engineer"),
]

EXPECTED = {
    "admin": {
        "组织与账号": ["团队架构", "账号管理", "角色与权限", "注册审批"],
        "审批与合规": ["审批中心", "审批范围", "审计日志"],
        "表单中心": ["待我处理", "我发起的", "生成表单", "表单模板"],
        "平台设置": ["系统设置", "标签管理", "数据导入"],
    },
    "leader": {
        "组织与账号": ["团队架构", "账号管理", "角色与权限", "注册审批"],
        "审批与合规": ["审批中心", "审批范围", "审计日志"],
        "表单中心": ["待我处理", "我发起的", "生成表单"],
        "平台设置": ["数据导入"],
    },
    "engineer": {
        "组织与账号": ["团队架构"],
        "审批与合规": ["审批中心", "审批范围"],
        "表单中心": ["待我处理", "我发起的", "生成表单"],
        "平台设置": ["数据导入"],
    },
}

OPEN_ALL = """
(() => {
  const menus = Array.from(document.querySelectorAll('.ant-menu'));
  const menu = menus.find((m) => m.offsetParent !== null) || menus[0];
  if (!menu) return false;
  menu.querySelectorAll('.ant-menu-submenu-title').forEach((el) => {
    if (!el.closest('.ant-menu-submenu-open')) el.click();
  });
  return true;
})()
"""

READ_STATE = """
(() => {
  const menus = Array.from(document.querySelectorAll('.ant-menu'));
  const menu = menus.find((m) => m.offsetParent !== null) || menus[0];
  if (!menu) return null;
  const groupTitle = (name) => {
    const groups = Array.from(menu.querySelectorAll('.ant-menu-item-group'));
    return groups.find((el) => (el.querySelector('.ant-menu-item-group-title')?.innerText || '').trim() === name) || null;
  };
  const itemsOf = (scope) => Array.from(scope.querySelectorAll('.ant-menu-item'))
    .map((el) => el.innerText.trim()).filter(Boolean);
  const knowledge = groupTitle('知识与资源');
  const system = groupTitle('系统与管理');
  const bell = document.querySelector('[data-testid=header-notifications-badge]');
  const notifItem = Array.from(menu.querySelectorAll('.ant-menu-item'))
    .find((i) => (i.innerText || '').includes('系统消息'));
  const notifBadge = notifItem && notifItem.querySelector('.ant-badge-count');
  return JSON.stringify({
    allText: menu.innerText,
    knowledgeItems: knowledge ? itemsOf(knowledge) : [],
    systemLeaves: system ? Array.from(system.querySelectorAll('.ant-menu-item')).filter((el) => !el.closest('.ant-menu-submenu')).map((el) => el.innerText.trim()).filter(Boolean) : [],
    submenuChildren: Object.fromEntries(Array.from(menu.querySelectorAll('.ant-menu-submenu')).map((el) => [
      (el.querySelector('.ant-menu-submenu-title')?.innerText || '').trim(),
      Array.from(el.querySelectorAll('.ant-menu-item')).map((i) => i.innerText.trim()).filter(Boolean),
    ])),
    openSubmenus: Array.from(menu.querySelectorAll('.ant-menu-submenu-open > .ant-menu-submenu-title'))
      .map((el) => el.innerText.trim()),
    selected: Array.from(menu.querySelectorAll('.ant-menu-item-selected')).map((el) => el.innerText.trim()),
    bellCount: bell ? bell.getAttribute('data-count') : null,
    hasBell: !!document.querySelector('[data-testid=header-notifications]'),
    sidebarNotifBadge: notifBadge ? (notifBadge.getAttribute('title') || notifBadge.innerText).trim() : null,
  });
})()
"""


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

    def goto(self, url, wait=3.5):
        self.call("Page.navigate", url=url)
        time.sleep(wait)


def api(path, method="GET", body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method,
                                     headers={"Content-Type": "application/json",
                                              **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(request, timeout=20) as resp:
        return json.loads(resp.read().decode())


def token_for(user, password):
    return api("/api/v1/auth/login", "POST", {"username": user, "password": password})["data"]["token"]


def main() -> int:
    proc = subprocess.Popen([EDGE, "--headless=new", "--disable-gpu", "--no-sandbox",
                             f"--remote-debugging-port={PORT}", f"--user-data-dir={PROFILE}", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    failures = []
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

        for user, password, role in ACCOUNTS:
            token = token_for(user, password)
            cdp.goto(BASE + "/login", 2.5)
            cdp.ev(f"localStorage.setItem('access_token', {json.dumps(token)}), true")
            cdp.goto(BASE + "/dashboard", 4.5)
            cdp.ev(OPEN_ALL)
            time.sleep(0.8)
            state = json.loads(cdp.ev(READ_STATE) or "null")
            if state is None:
                failures.append(f"{role}: 侧栏未渲染")
                continue

            print(f"\n===== {role} ({user}) =====")
            for name, kids in (state["submenuChildren"] or {}).items():
                print(f"   · {name}: {kids}")
            print("  knowledge:", state["knowledgeItems"], "| system leaves:", state["systemLeaves"])
            print("  bell:", state["hasBell"], "count:", state["bellCount"])

            for parent, kids in EXPECTED[role].items():
                if state["submenuChildren"].get(parent) != kids:
                    failures.append(
                        f"{role}: 「{parent}」子项不符 -> {state['submenuChildren'].get(parent)} 期望 {kids}")

            if state["knowledgeItems"] != ["安全知识库", "规范与文档", "工具与组件库"]:
                failures.append(f"{role}: 知识与资源项不符 -> {state['knowledgeItems']}")
            if state["systemLeaves"] != ["系统消息"]:
                failures.append(f"{role}: 系统与管理下的独立项不符 -> {state['systemLeaves']}")
            if not state["hasBell"]:
                failures.append(f"{role}: 顶栏铃铛缺失")

            # 角标联动：派发未读数事件 → 顶栏与侧栏同时变化
            cdp.ev("window.dispatchEvent(new CustomEvent('notifications:unread-changed', { detail: 7 })), true")
            time.sleep(1.2)
            linked = json.loads(cdp.ev(READ_STATE))
            print("  badge linkage:", linked["bellCount"], linked["sidebarNotifBadge"])
            if str(linked["bellCount"]) != "7" or not str(linked["sidebarNotifBadge"] or "").strip().endswith("7"):
                failures.append(
                    f"{role}: 角标未联动 -> bell={linked['bellCount']} sidebar={linked['sidebarNotifBadge']}")

            # 子页面自动展开 + 叶子高亮（管理员）
            if role == "admin":
                for path, parent, leaf in [("/teams", "组织与账号", "团队架构"),
                                           ("/forms/new", "表单中心", "生成表单"),
                                           ("/audit-logs", "审批与合规", "审计日志")]:
                    cdp.goto(BASE + path, 3.5)
                    s2 = json.loads(cdp.ev(READ_STATE))
                    if parent not in s2["openSubmenus"]:
                        failures.append(f"{role}: {path} 未自动展开「{parent}」→ {s2['openSubmenus']}")
                    if leaf not in s2["selected"]:
                        failures.append(f"{role}: {path} 未高亮「{leaf}」→ {s2['selected']}")
                    print(f"  {path}: open={s2['openSubmenus']} selected={s2['selected']}")

        cdp.ws.close()
        if failures:
            print("\nFAILURES:")
            for f in failures:
                print("  -", f)
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