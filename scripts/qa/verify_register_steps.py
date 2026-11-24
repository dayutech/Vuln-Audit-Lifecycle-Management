#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""t189 注册页两步流程的真机验证（headless Edge + CDP）。

前置：本机已安装 Edge；用 --remote-debugging-port 启动；页面地址为线上 8089。
用法：python verify_register_steps.py
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
BASE = "http://192.168.188.226:8089"
DEBUG_PORT = 9333
PROFILE = r"C:\Users\leo\tools\edge-cdp-profile"


def start_edge():
    proc = subprocess.Popen([
        EDGE, "--headless=new", "--disable-gpu", "--no-sandbox",
        f"--remote-debugging-port={DEBUG_PORT}",
        f"--user-data-dir={PROFILE}",
        "about:blank",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(50):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{DEBUG_PORT}/json/version", timeout=1) as resp:
                json.loads(resp.read().decode())
                return proc
        except Exception:
            time.sleep(0.2)
    raise RuntimeError("Edge 调试端口未就绪")


def ws_url():
    with urllib.request.urlopen(f"http://127.0.0.1:{DEBUG_PORT}/json", timeout=5) as resp:
        tabs = [t for t in json.loads(resp.read().decode()) if t.get("type") == "page"]
    return tabs[0]["webSocketDebuggerUrl"]


class Cdp:
    def __init__(self, url):
        self.ws = websocket.create_connection(url, timeout=30)
        self.id = 0

    def call(self, method, **params):
        self.id += 1
        self.ws.send(json.dumps({"id": self.id, "method": method, "params": params}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.id:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result", {})

    def eval(self, expression):
        result = self.call("Runtime.evaluate", expression=expression, returnByValue=True, awaitPromise=True)
        return result.get("result", {}).get("value")

    def navigate(self, url):
        self.call("Page.navigate", url=url)
        time.sleep(4)

    def close(self):
        self.ws.close()


FILL = """
(() => {
  const setValue = (sel, value) => {
    const el = document.querySelector(sel);
    if (!el) return 'missing:' + sel;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return 'ok';
  };
  return JSON.stringify({
    username: setValue('[data-testid=register-username]', 'cdp_probe_user'),
    password: setValue('[data-testid=register-password]', '%s'),
    confirm: setValue('[data-testid=register-confirm-password]', '%s'),
  });
})()
"""

STATE = """
(() => {
  const visible = (sel) => {
    const el = document.querySelector(sel);
    if (!el) return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && getComputedStyle(el).display !== 'none';
  };
  const text = document.body.innerText;
  return JSON.stringify({
    step: (document.querySelector('[data-testid=register-steps]') || {}).dataset ? document.querySelector('[data-testid=register-steps]').dataset.step : null,
    accountVisible: visible('[data-testid=register-username]'),
    phoneVisible: visible('[data-testid=register-phone]'),
    emailVisible: visible('[data-testid=register-email]'),
    nextBtn: !!document.querySelector('[data-testid=register-next]'),
    submitBtn: !!document.querySelector('[data-testid=register-submit]'),
    backBtn: !!document.querySelector('[data-testid=register-back]'),
    passwordErrorShown: text.includes('至少 8 位'),
    contactSendBtn: !!document.querySelector('[data-testid=register-send-sms]'),
  });
})()
"""

CLICK_NEXT = "document.querySelector('[data-testid=register-next]')?.click(), true"


def main() -> int:
    proc = start_edge()
    try:
        cdp = Cdp(ws_url())
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")

        # ── 场景 1：弱密码点「下一步」必须被拦住，且错误文案出现在字段下 ──
        cdp.navigate(f"{BASE}/register")
        print("fill weak password:", cdp.eval(FILL % ("weak", "weak")))
        time.sleep(0.5)
        cdp.eval(CLICK_NEXT)
        time.sleep(1.5)
        state = json.loads(cdp.eval(STATE))
        print("after weak attempt:", json.dumps(state, ensure_ascii=False))
        assert state["phoneVisible"] is False, "弱密码不应进入第 2 步"
        assert state["passwordErrorShown"] is True, "弱密码应在字段下显示规则错误"

        # ── 场景 2：合规账号 → 进入第 2 步，联系方式才出现 ──
        cdp.navigate(f"{BASE}/register")
        print("fill strong password:", cdp.eval(FILL % ("OwlAudit#2026", "OwlAudit#2026")))
        time.sleep(0.5)
        cdp.eval(CLICK_NEXT)
        time.sleep(1.5)
        state = json.loads(cdp.eval(STATE))
        print("after valid account:", json.dumps(state, ensure_ascii=False))
        assert state["step"] == "2", "合规账号应进入第 2 步"
        assert state["phoneVisible"] is True and state["emailVisible"] is True, "第 2 步应显示联系方式"
        assert state["accountVisible"] is False, "第 2 步不应再占位显示账号字段"
        assert state["submitBtn"] is True and state["nextBtn"] is False
        assert state["backBtn"] is True, "第 2 步应可返回"

        # ── 场景 3：「上一步」回到账号信息 ──
        cdp.eval("document.querySelector('[data-testid=register-back]')?.click(), true")
        time.sleep(1.0)
        state = json.loads(cdp.eval(STATE))
        print("after back:", json.dumps(state, ensure_ascii=False))
        assert state["step"] == "1" and state["accountVisible"] is True

        print("ALL CHECKS PASSED")
        cdp.call("Page.captureScreenshot", format="png")
        cdp.close()
        return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()


if __name__ == "__main__":
    sys.exit(main())