#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""t192 真机验证：客户/项目/资产新建页的编码自动生成与只读、客户联系人必填、操作条无阴影。

用法：python scripts/qa/verify_code_autofill.py
前置：线上 8089 可访问；admin/admin 可登录。
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://192.168.188.226:8089"
PORT = 9342
PROFILE = r"C:\Users\leo\tools\edge-cdp-autofill"

WORD_RE = {
    "customer": r"CUST-[A-Z]{7}",
    "project": r"PRJ-\d{4}-\d{2}-\d{2}-[A-Z]{6}",
    "asset": r"AST-\d{8}-[A-Z0-9]{6}",
}

READ_STATE = """
((codeTestId) => {
  const el = document.querySelector(`[data-testid=${codeTestId}]`);
  const actions = document.querySelector('.page-form__actions');
  const style = actions ? getComputedStyle(actions) : null;
  return JSON.stringify({
    code: el ? el.value : null,
    readOnly: el ? (el.readOnly === true) : null,
    disabled: el ? (el.disabled === true) : null,
    placeholder: el ? el.placeholder : null,
    shadow: style ? style.boxShadow : null,
    borderTop: style ? style.borderTopWidth + ' ' + style.borderTopStyle : null,
    radius: style ? style.borderRadius : null,
  });
})(${arguments} || null)
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

    def goto(self, url, wait=4.0):
        self.call("Page.navigate", url=url)
        time.sleep(wait)


def login(user, password):
    req = urllib.request.Request(BASE + "/api/v1/auth/login",
                                 data=json.dumps({"username": user, "password": password}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())["data"]["token"]


def state(cdp, code_testid):
    expr = "(() => {" \
           "const el = document.querySelector('[data-testid=" + code_testid + "]');" \
           "const actions = document.querySelector('.page-form__actions');" \
           "const style = actions ? getComputedStyle(actions) : null;" \
           "return JSON.stringify({" \
           "code: el ? el.value : null," \
           "readOnly: el ? el.readOnly === true : null," \
           "placeholder: el ? el.placeholder : null," \
           "shadow: style ? style.boxShadow : null," \
           "borderTop: style ? style.borderTopWidth + ' ' + style.borderTopStyle : null," \
           "radius: style ? style.borderRadius : null" \
           "});})()"
    return json.loads(cdp.ev(expr))



def check_edit_pages(cdp, token, failures):
    """编辑页：编码应回填且不可编辑（客户禁用 / 资产只读）。"""
    import urllib.request as u

    def api(path):
        req = u.Request(BASE + path, headers={"Authorization": f"Bearer {token}"})
        with u.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())["data"]["content"]

    asset = api("/api/v1/assets?page=0&size=1")[0]
    customer = api("/api/v1/customers?page=0&size=1")[0]

    cdp.goto(BASE + f"/customers/{customer['id']}/edit", 5.0)
    st = state(cdp, "customer-form-code")
    print(f"customer-edit code={st['code']!r} readonly={st['readOnly']} disabled={st.get('disabled')}")
    if st["code"] != customer["code"]:
        failures.append(f"客户编辑页编码未回填 -> {st['code']!r} != {customer['code']!r}")

    cdp.goto(BASE + f"/assets/{asset['id']}/edit", 6.0)
    st = state(cdp, "asset-form-code")
    print(f"asset-edit    code={st['code']!r} readonly={st['readOnly']}")
    if st["code"] != asset["code"]:
        failures.append(f"资产编辑页编码未回填 -> {st['code']!r} != {asset['code']!r}")
    if st["readOnly"] is not True:
        failures.append("资产编辑页编码应只读")

def main() -> int:
    token = login("admin", "admin")
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

        cases = [
            ("customer", "/customers/new", "customer-form-code"),
            ("project", "/projects/new", "project-form-code"),
            ("asset", "/assets/new", "asset-form-code"),
        ]
        import re
        for name, path, testid in cases:
            cdp.goto(BASE + path, 5.0)
            st = state(cdp, testid)
            print(f"{name:<9} code={st['code']!r} readOnly={st['readOnly']} placeholder={st['placeholder']!r}")
            if not st["code"] or not re.fullmatch(WORD_RE[name], st["code"]):
                failures.append(f"{name}: 编码未自动填充或格式不符 -> {st['code']!r}")
            if st["readOnly"] is not True:
                failures.append(f"{name}: 编码输入框不是只读 -> readOnly={st['readOnly']}")

        # 操作条样式（以客户新建页为准）
        print("action bar:", st["shadow"], "|", st["borderTop"], "| radius", st["radius"])
        if st["shadow"] not in ("none", None):
            failures.append(f"操作条仍有阴影 -> {st['shadow']}")
        if st["borderTop"] and not st["borderTop"].startswith("0px"):
            failures.append(f"操作条仍有上边框 -> {st['borderTop']}")
        if st["radius"] and st["radius"] not in ("0px",):
            failures.append(f"操作条仍有圆角 -> {st['radius']}")

        # 客户联系人 / 联系电话 必填：空表单提交应出现两条错误
        cdp.goto(BASE + "/customers/new", 5.0)
        cdp.ev("document.querySelector('[data-testid=customer-form-shell-submit]')?.click(), true")
        time.sleep(1.5)
        text = cdp.ev("document.querySelector('[data-testid=customer-form]')?.innerText || ''") or ""
        for keyword in ("请输入客户联系人", "请输入联系电话"):
            print(f"required check: {keyword!r} present={keyword in text}")
            if keyword not in text:
                failures.append(f"客户表单缺少必填提示: {keyword}")

        check_edit_pages(cdp, token, failures)

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