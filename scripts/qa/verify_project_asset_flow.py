#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""t193 真机验证：
1) 客户详情 → 新建项目：所属客户锁定且与来源客户一致；
2) 新建项目页提供「合同附件」上传入口；
3) 项目详情 → 添加资产弹框提供「新建资产」，进入后客户/项目锁定；
4) 资产新建页：编码预填只读、优先级/状态/类型/来源显示本地化标签（默认 P2 而非 medium）。
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://192.168.188.226:8089"
PORT = 9344
PROFILE = r"C:\Users\leo\tools\edge-cdp-t193"


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


def api(path, token):
    req = urllib.request.Request(BASE + path, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


def login(user="admin", password="admin"):
    req = urllib.request.Request(BASE + "/api/v1/auth/login",
                                 data=json.dumps({"username": user, "password": password}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())["data"]["token"]


JS = {
    # 项目新建页：客户选择器是否禁用 + 显示文本 + 锁定提示 + 合同上传入口
    "project_form": """
    (() => {
      const sel = document.querySelector('[data-testid=project-form-customer-select]');
      const locked = sel ? !!sel.closest('.ant-select-disabled') : null;
      const text = sel ? (sel.closest('.ant-select')?.innerText || '') : null;
      const body = document.body.innerText;
      return JSON.stringify({
        customerDisabled: locked,
        customerText: text,
        lockHint: body.includes('所属客户已锁定'),
        contractUpload: !!document.querySelector('[data-testid=project-form-contract-upload]'),
        readonlyCode: !!document.querySelector('[data-testid=project-form-code][readonly]'),
      });
    })()
    """,
    # 资产新建页：客户/项目锁定 + 各枚举标签 + 编码
    "asset_form": """
    (() => {
      const txt = (testid) => {
        const el = document.querySelector(`[data-testid=${testid}]`);
        return el ? (el.closest('.ant-select')?.innerText || '').trim() : null;
      };
      const disabled = (testid) => {
        const el = document.querySelector(`[data-testid=${testid}]`);
        return el ? !!el.closest('.ant-select-disabled') : null;
      };
      const code = document.querySelector('[data-testid=asset-form-code]');
      return JSON.stringify({
        customerDisabled: disabled('asset-form-customer-select'),
        projectDisabled: disabled('asset-form-project-select'),
        customerText: txt('asset-form-customer-select'),
        projectText: txt('asset-form-project-select'),
        priority: txt('asset-form-priority'),
        status: txt('asset-form-status'),
        targetType: txt('asset-form-target-type'),
        targetSource: txt('asset-form-target-source'),
        code: code ? code.value : null,
        codeReadonly: code ? code.readOnly : null,
        body: document.body.innerText.slice(0, 300),
      });
    })()
    """,
}


def main() -> int:
    token = login()
    customers = api("/api/v1/customers?page=0&size=1", token)["data"]["content"]
    if not customers:
        print("no customer to test")
        return 2
    customer = customers[0]
    projects = api(f"/api/v1/projects?page=0&size=5&customerId={customer['id']}", token)["data"]["content"]
    if not projects:
        print("无可用项目（客户下没有项目），跳过项目侧验证")
        return 2
    project = projects[0]

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
        cdp.goto(BASE + "/login", 2.5)
        cdp.ev(f"localStorage.setItem('access_token', {json.dumps(token)}), true")

        # ① 客户详情 → 新建项目
        cdp.goto(BASE + f"/customers/{customer['id']}", 5.0)
        clicked = cdp.ev("(() => { const b = document.querySelector('[data-testid=customer-detail-new-project]'); if (!b) return false; b.click(); return true; })()")
        time.sleep(3.0)
        print("\n== ① 客户详情 → 新建项目 ==")
        print("   button clicked:", clicked, "url:", cdp.ev("location.pathname + location.search"))
        pf = json.loads(cdp.ev(JS["project_form"]))
        print("  ", json.dumps(pf, ensure_ascii=False))
        if pf["customerDisabled"] is not True:
            failures.append("新建项目页的所属客户没有锁定（应 disabled）")
        if customer["name"] not in (pf["customerText"] or ""):
            failures.append(f"所属客户不是来源客户：显示 {pf['customerText']!r} != {customer['name']!r}")
        if not pf["lockHint"]:
            failures.append("缺少「所属客户已锁定」提示")
        if not pf["contractUpload"]:
            failures.append("新建项目页缺少合同附件上传入口")

        # ② 项目详情 → 添加资产弹框 → 新建资产
        cdp.goto(BASE + f"/projects/{project['id']}", 5.5)
        opened = cdp.ev("(() => { const b = Array.from(document.querySelectorAll('button')).find(x => (x.innerText||'').includes('添加资产')); if (!b) return false; b.click(); return true; })()")
        time.sleep(2.0)
        has_create = cdp.ev("!!document.querySelector('[data-testid=project-asset-create-new]')")
        print("\n== ② 项目详情 → 添加资产 ==")
        print("   添加资产按钮:", opened, "| 弹框内「新建资产」:", has_create)
        if not has_create:
            failures.append("添加资产弹框缺少「新建资产」入口")
        else:
            # 点击「新建资产」应直接跳到资产新建页，并带上来源客户/项目与回跳地址
            cdp.ev("document.querySelector('[data-testid=project-asset-create-new]').click(), true")
            time.sleep(3.0)
            url = cdp.ev("location.pathname + location.search")
            print("   after click url:", url)
            if "/assets/new" not in (url or "") or f"projectId={project['id']}" not in (url or ""):
                failures.append(f"「新建资产」未跳转到预填的资产新建页: {url!r}")
            af = json.loads(cdp.ev(JS["asset_form"]))
            print("\n== ③ 资产新建页（来自项目） ==")
            print("  ", json.dumps({k: v for k, v in af.items() if k != 'body'}, ensure_ascii=False))
            if af["customerDisabled"] is not True or af["projectDisabled"] is not True:
                failures.append("资产新建页的客户/项目未锁定")
            if customer["name"] not in (af["customerText"] or ""):
                failures.append(f"资产页客户显示不符: {af['customerText']!r}")
            if not af["projectText"]:
                failures.append("资产页项目未预填")
            if "P2" not in (af["priority"] or "") or "medium" in (af["priority"] or "").lower():
                failures.append(f"优先级未本地化/默认值不对: {af['priority']!r}")
            for label, field in [("活跃", "status"), ("网站", "targetType"), ("客户", "targetSource")]:
                if label not in (af[field] or ""):
                    failures.append(f"{field} 未本地化: {af[field]!r}")
            if not af["code"] or af["codeReadonly"] is not True:
                failures.append(f"资产编码未预填/只读: {af['code']!r} readonly={af['codeReadonly']}")

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