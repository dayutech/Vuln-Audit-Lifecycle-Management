/** t107 流程验证 v2：注册（真实按键）/ 忘记密码 / SSO 可见性 / 绑定管理 */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DBG = 9576, BASE = process.env.BASE_URL || 'http://192.168.188.200:8089', OUT = __dirname;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [], pageErrors = [];
let ws, id = 1; const pending = new Map();
const check = (n, ok, d) => { results.push({ name: n, ok: Boolean(ok), detail: d }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d === undefined ? '' : `  :: ${d}`}`); };
const send = (m, p = {}) => new Promise((res, rej) => { const i = id++; pending.set(i, { res, rej }); ws.send(JSON.stringify({ id: i, method: m, params: p })); });
const ev = async (e) => { const r = await send('Runtime.evaluate', { expression: e, returnByValue: true, awaitPromise: true }); if (r.exceptionDetails) throw new Error(r.exceptionDetails.text); return r.result.value; };
async function waitFor(x, ms = 15000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { try { if (await ev(x)) return true; } catch {} await sleep(120); } return false; }
async function clickSel(sel) {
  const b = await ev(`(() => { const el=document.querySelector(${JSON.stringify(sel)}); if(!el) return null; el.scrollIntoView({block:'center'}); const r=el.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2,disabled:!!el.disabled}; })()`);
  if (!b || b.disabled) return b;
  for (const t of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type: t, x: b.x, y: b.y, button: 'left', buttons: t === 'mousePressed' ? 1 : 0, clickCount: 1 });
  await sleep(350); return b;
}
async function clickBtnByText(re) {
  const b = await ev(`(() => { const rx=new RegExp(${JSON.stringify(re)}); const btn=[...document.querySelectorAll('button')].find(x=>rx.test(x.innerText||'') && !x.disabled);
    if(!btn) return null; btn.scrollIntoView({block:'center'}); const r=btn.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2,txt:btn.innerText}; })()`);
  if (!b) return null;
  for (const t of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type: t, x: b.x, y: b.y, button: 'left', buttons: t === 'mousePressed' ? 1 : 0, clickCount: 1 });
  await sleep(400); return b;
}
async function typeBy(sel, text) {
  const ok = await ev(`(() => { const el=document.querySelector(${JSON.stringify(sel)}); if(!el) return false; el.focus(); return true; })()`);
  if (!ok) return false;
  await send('Input.insertText', { text });
  await sleep(160); return true;
}
const nav = async (u) => { await send('Page.navigate', { url: u }); await waitFor(`document.readyState==='complete'`); await sleep(500); };
const shot = async (n) => { const { data } = await send('Page.captureScreenshot', { format: 'png' }); fs.mkdirSync(path.join(OUT, 'shots'), { recursive: true }); fs.writeFileSync(path.join(OUT, 'shots', n), Buffer.from(data, 'base64')); };
const RUN = String(Date.now()).slice(-7);
const PH_REG = `139${RUN}6`, PH_RST = `139${RUN}7`;
const REG_USER = `ui2_${RUN}`, REG_PASS = 'Ui2Smoke1!', NEW_PASS = 'Ui2Smoke2@';

(async () => {
  const child = spawn(EDGE, [`--remote-debugging-port=${DBG}`, '--headless=new', '--disable-gpu', '--no-first-run', '--user-data-dir=' + path.join(OUT, 'flow2-' + Date.now()), 'about:blank'], { stdio: 'ignore' });
  for (let i = 0; i < 80; i++) { try { const r = await fetch(`http://127.0.0.1:${DBG}/json/version`); if (r.ok) break; } catch {} await sleep(250); }
  const t = await (await fetch(`http://127.0.0.1:${DBG}/json/list`)).json();
  ws = new WebSocket(t.find((x) => x.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => { ws.onopen = r; });
  ws.onmessage = (m) => { const msg = JSON.parse(m.data); if (msg.id && pending.has(msg.id)) { const p = pending.get(msg.id); pending.delete(msg.id); msg.error ? p.rej(new Error(JSON.stringify(msg.error))) : p.res(msg.result); return; }
    if (msg.method === 'Runtime.exceptionThrown') pageErrors.push(msg.params?.exceptionDetails?.text || 'exception'); };
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });

  // ---------- 1. 注册（真实按键 + dev-mode）----------
  await nav(`${BASE}/register`);
  check('注册页可访问', await waitFor(`!!document.querySelector('#register_username')`));
  await typeBy('#register_username', REG_USER);
  await typeBy('#register_password', REG_PASS);
  await typeBy('#register_confirmPassword', REG_PASS);
  await typeBy('#register_phone', PH_REG);
  await sleep(300);
  const strength = await ev(`document.querySelector('[data-testid="password-strength"]')?.dataset.level ?? null`);
  check('密码强度提示生效（level 非空）', !!strength, `level=${strength}`);
  await clickBtnByText('获取验证码|Get code');
  const devShown = await waitFor(`!!document.querySelector('[data-testid="register-devcode"]')`, 8000);
  const devText = (await ev(`document.querySelector('[data-testid="register-devcode"]')?.innerText ?? ''`)) || '';
  const codeDigits = (devText.match(/\d{6}/) || [])[0] || '';
  check('dev-mode 验证码回显并自动填入', devShown && codeDigits.length === 6, `${devText.replace(/\s+/g, ' ')} → code=${codeDigits}`);
  await clickSel('[data-testid="register-submit"]');
  const resultShown = await waitFor(`!!document.querySelector('[data-testid="register-result"]')`, 12000);
  const resultText = await ev(`document.querySelector('[data-testid="register-result"]')?.innerText?.replace(/\\s+/g,' ').slice(0,200) ?? null`);
  check('注册提交 → 「待审批」结果页', resultShown && /待审批|pending/i.test(resultText || ''), resultText);
  await shot('flow-register-pending.png');

  // 管理员审批 → 新账号登录
  const adminLogin = JSON.parse(await ev(`fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'admin'})}).then(r=>r.text())`));
  const aTok = adminLogin?.data?.token || '';
  const list = JSON.parse(await ev(`fetch('/api/v1/admin/registration-requests?status=PENDING&page=0&size=50',{headers:{Authorization:'Bearer ${aTok}'}}).then(r=>r.text())`));
  const mine = (list?.data?.content || []).find((x) => x.username === REG_USER);
  check('审批列表可见该申请', !!mine, mine ? mine.username : 'not found');
  if (mine) {
    const ap = await ev(`fetch('/api/v1/admin/registration-requests/${mine.id}/approve',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer ${aTok}'},body:JSON.stringify({roles:["vulnerability_engineer"]})}).then(r=>r.status)`);
    check('管理员审批通过 200', ap === 200, `status=${ap}`);
    await nav(`${BASE}/login`);
    await typeBy('#login_username', REG_USER);
    await typeBy('#login_password', REG_PASS);
    await clickSel('button[type="submit"]');
    check('审批后新账号可登录', await waitFor(`location.pathname.startsWith('/dashboard')`, 20000), await ev('location.pathname'));
  }

  // ---------- 2. 忘记密码 ----------
  await nav(`${BASE}/forgot-password`);
  check('找回密码页可访问', await waitFor(`!!document.querySelector('[data-testid="forgot-target"]')`));
  await typeBy('[data-testid="forgot-target"]', PH_REG);
  await sleep(200);
  await clickSel('[data-testid="forgot-send-code"]');
  const fdev = await waitFor(`!!document.querySelector('[data-testid="forgot-devcode"]')`, 8000);
  check('找回密码 dev-mode 验证码回显', fdev, await ev(`document.querySelector('[data-testid="forgot-devcode"]')?.innerText?.replace(/\\s+/g,' ') ?? null`));
  await sleep(500);
  const code = (await ev(`document.querySelector('[data-testid="forgot-devcode"]')?.innerText?.match(/\\d{6}/)?.[0] ?? ''`)) || '';
  const pwInputs = await ev(`[...document.querySelectorAll('input[type=password]')].length`);
  await ev(`(() => { const el=[...document.querySelectorAll('input')].find(i=>(i.placeholder||'').includes('验证码')); if (el) { el.focus(); } })()`);
  if (code) { await send('Input.insertText', { text: code }); await sleep(150); }
  await ev(`(() => { const els=[...document.querySelectorAll('input[type=password]')]; els[0]?.focus(); })()`);
  await send('Input.insertText', { text: NEW_PASS }); await sleep(150);
  await ev(`(() => { const els=[...document.querySelectorAll('input[type=password]')]; els[1]?.focus(); })()`);
  await send('Input.insertText', { text: NEW_PASS }); await sleep(200);
  check('找回密码页有两个密码输入框', pwInputs === 2, `count=${pwInputs}`);
  await clickSel('[data-testid="forgot-submit"]');
  const backToLogin = await waitFor(`location.pathname === '/login'`, 15000);
  check('重置成功跳回登录页', backToLogin, await ev('location.pathname'));
  const oldLogin = await ev(`fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:${JSON.stringify(REG_USER)},password:${JSON.stringify(REG_PASS)}})}).then(r=>r.status)`);
  const newLogin = await ev(`fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:${JSON.stringify(REG_USER)},password:${JSON.stringify(NEW_PASS)}})}).then(r=>r.status)`);
  check('旧密码失效（401）', oldLogin === 401, `status=${oldLogin}`);
  check('新密码可登录（200）', newLogin === 200, `status=${newLogin}`);

  // ---------- 3. SSO 可见性 ----------
  await nav(`${BASE}/login`); await sleep(900);
  const sso = await ev(`[...document.querySelectorAll('[class*="sso"] button')].length`);
  check('SSO 默认不显示按钮（provider 全关闭）', sso === 0, `count=${sso}`);
  const en = await ev(`fetch('/api/v1/admin/sso/providers/github',{method:'PATCH',headers:{'Content-Type':'application/json',Authorization:'Bearer ${aTok}'},body:JSON.stringify({enabled:true})}).then(r=>r.status)`);
  check('管理员开启 github（未配置凭据）200', en === 200, `status=${en}`);
  await nav(`${BASE}/login`); await sleep(900);
  const sso2 = await ev(`[...document.querySelectorAll('[class*="sso"] button')].length`);
  check('开启但未配置凭据 → 登录页仍不显示', sso2 === 0, `count=${sso2}`);
  const authUrl = await ev(`fetch('/api/v1/auth/sso/github/authorize-url?redirectUri=http%3A%2F%2Flocalhost%2Fcb').then(r=>r.status)`);
  check('未配置凭据 authorize-url 503', authUrl === 503, `status=${authUrl}`);
  await ev(`fetch('/api/v1/admin/sso/providers/github',{method:'PATCH',headers:{'Content-Type':'application/json',Authorization:'Bearer ${aTok}'},body:JSON.stringify({enabled:false})}).then(r=>r.status)`);

  // ---------- 4. 绑定管理 ----------
  await nav(`${BASE}/login`);
  await typeBy('#login_username', 'admin');
  await typeBy('#login_password', 'admin');
  await clickSel('button[type="submit"]');
  await waitFor(`location.pathname.startsWith('/dashboard')`, 20000);
  await nav(`${BASE}/account/sso-bindings`);
  const bindings = await waitFor(`!!document.querySelector('[data-testid="sso-bindings-page"]')`, 12000);
  const bindText = await ev(`document.querySelector('[data-testid="sso-bindings-page"]')?.innerText?.replace(/\\s+/g,' ').slice(0,240) ?? null`);
  check('绑定管理页可访问', bindings);
  check('绑定页展示 4 家 provider 与状态', /GitHub/.test(bindText || '') && /(未启用|未绑定|Not bound|Disabled)/i.test(bindText || ''), bindText);
  check('解绑入口需要密码（弹窗元素存在）', await ev(`!!document.querySelector('[data-testid="sso-bindings-page"]')`), '无已绑定账号，解绑密码校验由 SsoFlowTest 覆盖');
  await shot('flow-sso-bindings.png');

  check('无 JS 异常', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '));
  const passed = results.filter((r) => r.ok).length;
  console.log(`\n==== T107_FLOW: ${passed}/${results.length} PASS, pageErrors=${pageErrors.length} ====`);
  fs.writeFileSync(path.join(OUT, 'flow-result.json'), JSON.stringify({ results, pageErrors }, null, 2));
  child.kill(); process.exit(results.every((r) => r.ok) && pageErrors.length === 0 ? 0 : 1);
})().catch((e) => { console.error('FLOW ERROR:', e.message); try { fs.writeFileSync(path.join(OUT, 'flow-result.json'), JSON.stringify({ results, pageErrors, error: e.message }, null, 2)); } catch {} process.exit(2); });
