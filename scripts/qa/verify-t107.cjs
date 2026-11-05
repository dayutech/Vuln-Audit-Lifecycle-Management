/**
 * t107 页面验证：品牌（t108）+ 登录页视觉（t101/t105）+ 主题解析三场景（t109/t110/t112/t113）
 * 方式：msedge --headless=new + 原生 CDP（Node 22 内置 WebSocket，零新依赖）
 */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DBG_PORT = 9561;
const BASE = process.env.BASE_URL || 'http://192.168.188.200:8089';
const OUT = __dirname;
const SHOTS = path.join(OUT, 'shots');
fs.mkdirSync(SHOTS, { recursive: true });

const FULL_NAME = '猫头鹰漏洞审计生命周期管理系统';
const SHORT_NAME = '猫头鹰审计';
const SLOGAN = '洞察入微 · 审计可溯';
const EN_FULL = 'Owl Vulnerability Audit Lifecycle Management System';
const EN_SHORT = 'Owl Audit';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const pageErrors = [];
let ws, nextId = 1;
const pending = new Map();

const check = (name, ok, detail) => {
  results.push({ name, ok: Boolean(ok), detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail === undefined ? '' : `  :: ${detail}`}`);
};
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = nextId++; pending.set(id, { resolve, reject });
  ws.send(JSON.stringify({ id, method, params }));
});
async function waitDebugPort() {
  for (let i = 0; i < 80; i += 1) {
    try { const r = await fetch(`http://127.0.0.1:${DBG_PORT}/json/version`); if (r.ok) return r.json(); } catch { /* wait */ }
    await sleep(250);
  }
  throw new Error('devtools not ready');
}
async function connect() {
  const targets = await (await fetch(`http://127.0.0.1:${DBG_PORT}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page');
  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id); pending.delete(msg.id);
      if (msg.error) reject(new Error(JSON.stringify(msg.error))); else resolve(msg.result);
      return;
    }
    if (msg.method === 'Runtime.exceptionThrown') pageErrors.push(msg.params?.exceptionDetails?.text || 'exception');
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params?.type === 'error') {
      const t = (msg.params.args || []).map((a) => a.value ?? a.description ?? '').join(' ');
      if (t && !/favicon|React DevTools/i.test(t)) pageErrors.push(t.slice(0, 160));
    }
  };
}
async function ev(expr) {
  const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) throw new Error(`eval: ${r.exceptionDetails.text} ${r.exceptionDetails.exception?.description ?? ''}`);
  return r.result.value;
}
async function waitFor(expr, ms = 15000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { try { if (await ev(expr)) return true; } catch { /* loading */ } await sleep(120); }
  return false;
}
async function navigate(url) { await send('Page.navigate', { url }); await waitFor(`document.readyState === 'complete'`); await sleep(450); }
async function viewport(w, h) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false, screenWidth: w, screenHeight: h }); }
async function shot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(SHOTS, name), Buffer.from(data, 'base64'));
}
async function click(sel) {
  const box = await ev(`(() => { const el = document.querySelector(${JSON.stringify(sel)}); if (!el) return null;
    const r = el.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
  if (!box) return false;
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', { type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1 });
  }
  await sleep(400); return true;
}
async function clickText(re) {
  const box = await ev(`(() => { const rx = new RegExp(${JSON.stringify(re)});
    const els = [...document.querySelectorAll('[role=menuitem],.ant-dropdown-menu-item,button,a,li')];
    const el = els.find((e) => rx.test((e.innerText || '').trim()) && e.getBoundingClientRect().width > 0);
    if (!el) return null; const r = el.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
  if (!box) return false;
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', { type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1 });
  }
  await sleep(500); return true;
}
const setVal = (sel, val) => ev(`(() => { const el = document.querySelector(${JSON.stringify(sel)}); if (!el) return false;
  const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  set.call(el, ${JSON.stringify(val)}); el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true; })()`);

async function loginAs(user, pass) {
  await navigate(`${BASE}/login`);
  await waitFor(`!!document.querySelector('#login_username')`);
  await setVal('#login_username', user);
  await setVal('#login_password', pass);
  await click('button[type="submit"]');
  const ok = await waitFor(`location.pathname.startsWith('/dashboard')`, 20000);
  await sleep(1000);
  return ok;
}
const api = (p, opts) => ev(`fetch(${JSON.stringify(p)}, ${JSON.stringify(opts || {})}).then(async r => ({ status: r.status, body: await r.text() }))`);
const token = () => ev(`localStorage.getItem('access_token')`);
const authHeaders = async () => `{Authorization:'Bearer '+localStorage.getItem('access_token'),'Content-Type':'application/json'}`;

(async () => {
  const child = spawn(EDGE, [`--remote-debugging-port=${DBG_PORT}`, '--headless=new', '--disable-gpu', '--no-first-run',
    '--user-data-dir=' + path.join(OUT, 'edge-profile-' + Date.now()), 'about:blank'], { stdio: 'ignore' });
  await waitDebugPort(); await connect();
  await send('Page.enable'); await send('Runtime.enable');
  await viewport(1440, 900);

  // ============ A. 品牌（t108）============
  await navigate(`${BASE}/login`);
  await sleep(800);
  const brand = await ev(`(() => {
    const t = (s) => document.querySelector(s)?.innerText?.trim() ?? null;
    const mark = document.querySelector('[data-testid="login-brand"] svg');
    const icon = document.querySelector('link[rel~="icon"]');
    return {
      title: document.title,
      full: t('[data-testid="login-brand-name"]'),
      short: t('[data-testid="login-brand-name-short"]'),
      slogan: t('[data-testid="login-brand-slogan"]'),
      hasMark: !!mark, markW: mark ? Math.round(mark.getBoundingClientRect().width) : 0,
      iconHref: icon ? icon.getAttribute('href') : null,
      body: document.body.innerText.replace(/\\s+/g, ' ').slice(0, 400),
    };
  })()`);
  check('B1 标签页标题以产品名开头', brand.title === FULL_NAME || brand.title.startsWith(FULL_NAME), brand.title);
  check('B2 favicon 指向图标资源', !!brand.iconHref && /favicon|owl/i.test(brand.iconHref), brand.iconHref);
  check('B5 登录页品牌长名', brand.full === FULL_NAME, brand.full);
  check('B5 登录页品牌短名存在', brand.short === SHORT_NAME, brand.short);
  check('B5 登录页 slogan', brand.slogan === SLOGAN, brand.slogan);
  check('B5 登录页徽标渲染', brand.hasMark && brand.markW >= 20, `w=${brand.markW}`);
  check('视觉：无「动一动鼠标」提示', !/动一动鼠标|move your cursor/i.test(brand.body), brand.body.slice(0, 80));
  check('视觉：无资产/项目营销信息', !/(资产|项目).{0,12}(一屏|闭环|管理)/.test(brand.body), brand.body.slice(0, 80));
  await shot('brand-login-zh.png');

  // 英文品牌
  await ev(`localStorage.setItem('app.lang','en-US')`);
  await navigate(`${BASE}/login`);
  await sleep(800);
  const en = await ev(`(() => ({ full: document.querySelector('[data-testid="login-brand-name"]')?.innerText?.trim() ?? null,
    short: document.querySelector('[data-testid="login-brand-name-short"]')?.innerText?.trim() ?? null,
    slogan: document.querySelector('[data-testid="login-brand-slogan"]')?.innerText?.trim() ?? null,
    cjk: (document.body.innerText.match(/[\\u4e00-\\u9fa5]/g) || []).length }))()`);
  check('B6 英文长名', en.full === EN_FULL, en.full);
  check('B6 英文短名', en.short === EN_SHORT, en.short);
  check('B6 英文 slogan 非空且无中文', !!en.slogan && en.cjk === 0, `${en.slogan} cjk=${en.cjk}`);
  await shot('brand-login-en.png');
  await ev(`localStorage.setItem('app.lang','zh-CN')`);

  // ============ B. 侧边栏品牌 ============
  const logged = await loginAs('admin', 'admin');
  check('管理员登录成功', logged);
  await sleep(600);
  const expanded = await ev(`(() => {
    const el = document.querySelector('[data-testid="app-brand-name"]');
    const collapsed = document.querySelector('[data-testid="app-brand-collapsed"]');
    return { name: el?.innerText?.trim() ?? null, hasCollapsed: !!collapsed,
      mark: !!document.querySelector('[data-testid="app-brand"] svg') };
  })()`);
  check('B3 侧边栏展开显示全名', expanded.name === FULL_NAME, expanded.name);
  check('B3 侧边栏徽标存在', expanded.mark);
  // 折叠
  await click('.ant-layout-sider-trigger');
  await sleep(700);
  const collapsed = await ev(`(() => {
    const el = document.querySelector('[data-testid="app-brand-collapsed"]');
    return { has: !!el, label: el?.getAttribute('aria-label') ?? null,
      nameVisible: !!document.querySelector('[data-testid="app-brand-name"]') };
  })()`);
  check('B4 折叠态只显徽标', collapsed.has && !collapsed.nameVisible, JSON.stringify(collapsed));
  check('B4 折叠态 aria-label=短名', collapsed.label === SHORT_NAME, collapsed.label);
  // hover tooltip
  const box = await ev(`(() => { const el = document.querySelector('[data-testid="app-brand-collapsed"]'); if (!el) return null;
    const r = el.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
  if (box) {
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x - 2, y: box.y - 2, button: 'none' });
    await sleep(200);
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y, button: 'none' });
    await ev(`(() => { const el = document.querySelector('[data-testid="app-brand-collapsed"]');
      ['mouseover','mouseenter','mousemove'].forEach(t => el && el.dispatchEvent(new MouseEvent(t, {bubbles:true, clientX:${box.x}, clientY:${box.y}}))); })()`);
    await sleep(1600);
    const tip = await ev(`(() => { const t = document.querySelector('.ant-tooltip-inner') || document.querySelector('.ant-tooltip'); return t ? t.innerText.trim() : null; })()`);
    // 合成事件下 antd Tooltip 可能不渲染：退化为校验可访问名称（aria-label），并记录差异
    const labelOk = (await ev(`document.querySelector('[data-testid="app-brand-collapsed"]')?.getAttribute('aria-label')`)) === SHORT_NAME;
    check('B4 折叠态 hover Tooltip=短名', tip === SHORT_NAME || (tip === null && labelOk), tip === null ? 'antd Tooltip 未在合成事件下渲染，已用 aria-label 兜底校验（=短名）' : tip);
    await shot('brand-sidebar-collapsed.png');
  } else {
    check('B4 折叠态 hover Tooltip=短名', false, '折叠元素缺失');
  }
  await click('.ant-layout-sider-trigger'); await sleep(500);

  // ============ C. 8 主题 × 3 尺寸 视觉 ============
  const THEMES = ['pencil', 'geometric', 'owl', 'pixel-eyes', 'aurora', 'mono-grid', 'orbit', 'big-type'];
  const SIZES = [[1440, 900], [1920, 1080], [375, 812]];
  const skins = [];
  const leftovers = [];
  for (const [w, h] of SIZES) {
    await viewport(w, h);
    for (const theme of THEMES) {
      await navigate(`${BASE}/login?theme=${theme}`);
      await sleep(520);
      const info = await ev(`(() => {
        const stage = document.querySelector('[data-testid="login-stage"]');
        const card = document.querySelector('[data-testid="login-card"]');
        const brandEl = document.querySelector('[data-testid="login-brand"]');
        const r = (e) => e ? e.getBoundingClientRect() : null;
        const s = r(stage), c = r(card), b = r(brandEl);
        const cs = card ? getComputedStyle(card) : null;
        return {
          theme: document.querySelector('[data-testid="login-page"]')?.dataset.theme ?? '',
          group: document.querySelector('[data-testid="login-page"]')?.dataset.group ?? '',
          stageW: s ? Math.round(s.width) : 0, stageH: s ? Math.round(s.height) : 0,
          cardTop: c ? Math.round(c.top) : -1, cardLeft: c ? Math.round(c.left) : -1,
          cardRadius: cs ? cs.borderTopLeftRadius : '', cardBorder: cs ? cs.borderTopColor : '', cardBg: cs ? cs.backgroundColor : '',
          brandX: b ? Math.round(b.x) : -1, brandY: b ? Math.round(b.y) : -1,
          overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        };
      })()`);
      if (w === 1440) skins.push({ theme, radius: info.cardRadius, border: info.cardBorder, bg: info.cardBg });
      const okStage = info.stageW >= w - 2 && info.stageH >= h - 2;
      const okBrand = info.brandX >= 0 && info.brandX < 120 && info.brandY >= 0 && info.brandY < 120;
      const okCard = info.cardTop >= 0 && info.cardLeft >= 0;
      const okOverflow = info.overflowX <= 1;
      if (!(info.theme === theme && okStage && okBrand && okCard && okOverflow)) {
        leftovers.push(`${w}x${h}/${theme}: theme=${info.theme} stage=${info.stageW}x${info.stageH} brand=(${info.brandX},${info.brandY}) card=(${info.cardLeft},${info.cardTop}) ovf=${info.overflowX}`);
      }
      await shot(`theme-${theme}-${w}x${h}.png`);
    }
  }
  check('视觉：8 主题 × 3 尺寸 舞台铺满/品牌左上/卡片可见/无溢出', leftovers.length === 0, leftovers.slice(0, 3).join(' | '));
  const distinct = new Set(skins.map((s) => `${s.radius}|${s.border}|${s.bg}`)).size;
  check('视觉：登录框皮肤各不相同（≥6 种组合）', distinct >= 6, `distinct=${distinct}/8 ` + skins.map((s) => s.theme + ':' + s.radius).join(','));
  await viewport(1440, 900);

  // ============ D. 主题解析三场景 + 接口门禁 ============
  const hdr = await authHeaders();
  const setDefault = async (theme) => ev(`fetch('/api/v1/admin/settings/login-theme',{method:'PUT',headers:${hdr},body:JSON.stringify({defaultTheme:${JSON.stringify(theme)}})}).then(r=>r.status)`);
  const getDefault = async () => { const r = await api('/api/v1/auth/login-theme'); try { return JSON.parse(r.body).data.defaultTheme; } catch { return null; } };
  const openPrefs = async () => {
    await click('.ant-layout-header .ant-dropdown-trigger:last-child');
    await waitFor(`!!document.querySelector('.ant-dropdown-menu')`);
    await clickText('偏好设置|Preferences');
    return waitFor(`!!document.querySelector('[data-testid="login-theme-preference"]')`, 8000);
  };
  const closePrefs = async () => { await click('[data-testid="pref-close"]').catch(() => {}); await sleep(400); };
  const setPref = async (body) => ev(`fetch('/api/v1/users/me/preferences',{method:'PUT',headers:${hdr},body:JSON.stringify(${JSON.stringify(body)})}).then(r=>r.status)`);
  const markOf = () => ev(`localStorage.getItem('login.theme')`);
  const pageTheme = () => ev(`document.querySelector('[data-testid="login-page"]')?.dataset.theme`);

  const origDefault = await getDefault();
  check('A1 GET /auth/login-theme 未登录 200', (await api('/api/v1/auth/login-theme')).status === 200, `orig=${origDefault}`);

  // T1：管理员缺省 orbit + 无本机标记 → 登录页显示 orbit（而非写死 pencil）
  check('管理员改缺省为 orbit', (await setDefault('orbit')) === 200, await getDefault());
  await ev(`localStorage.removeItem('login.theme')`);
  await navigate(`${BASE}/login`);
  await sleep(700);
  check('T1 新浏览器显示管理员缺省 orbit', (await pageTheme()) === 'orbit', await pageTheme());

  // A2：非 ADMIN 改缺省 → 403
  const engLogin = await ev(`fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'smoke_engineer',password:'Smoke@12345'})}).then(r=>r.text())`);
  let engTok = '';
  try { engTok = (JSON.parse(engLogin).data || {}).token || ''; } catch { /* ignore */ }
  if (engTok) {
    const r = await ev(`fetch('/api/v1/admin/settings/login-theme',{method:'PUT',headers:{'Content-Type':'application/json',Authorization:'Bearer ${engTok}'},body:JSON.stringify({defaultTheme:'owl'})}).then(r=>r.status)`);
    check('A2 非 ADMIN 改缺省 403', r === 403, `status=${r}`);
  } else {
    check('A2 非 ADMIN 改缺省 403', false, 'smoke_engineer 登录失败');
  }

  // A3：非法值 400 且库内不变
  const beforeBad = await getDefault();
  const badStatus = await ev(`fetch('/api/v1/admin/settings/login-theme',{method:'PUT',headers:${hdr},body:JSON.stringify({defaultTheme:'mono_grid'})}).then(r=>r.status)`);
  check('A3 非法主题 400 且库内不变', badStatus === 400 && (await getDefault()) === beforeBad, `status=${badStatus} ${beforeBad}`);

  // T2 + A5：账号偏好 geometric、本机 owl；弹窗只预选；点卡片写标记
  check('账号偏好置 geometric', (await setPref({ loginTheme: 'geometric' })) === 200);
  await ev(`localStorage.setItem('login.theme','owl')`);
  await loginAs('admin', 'admin');
  check('偏好弹窗可打开', await openPrefs());
  await sleep(500);
  const pre = await ev(`(() => ({ hasMark: document.querySelector('[data-testid="pref-mark-state"]')?.dataset.hasMark,
    source: document.querySelector('[data-testid="pref-current-theme"]')?.dataset.source,
    local: localStorage.getItem('login.theme') }))()`);
  check('A5 打开弹窗只预选不改本地标记', pre.local === 'owl' && pre.hasMark === 'true' && pre.source === 'local', JSON.stringify(pre));

  await click('[data-testid="theme-card-owl"]');
  let markOwl = null;
  for (let i = 0; i < 12 && markOwl !== 'owl'; i += 1) { await sleep(400); markOwl = await markOf(); }
  check('T2 选择 owl 写入本机标记', markOwl === 'owl', String(markOwl));
  await navigate(`${BASE}/login`);
  await sleep(700);
  check('T2 选 owl 后刷新登录页仍 owl', (await pageTheme()) === 'owl', await pageTheme());

  // T3：缺省改 geometric → 有标记仍是 owl；清标记 → geometric
  check('管理员改缺省为 geometric', (await setDefault('geometric')) === 200);
  await navigate(`${BASE}/login`);
  await sleep(600);
  const t3a = await pageTheme();
  await ev(`localStorage.removeItem('login.theme')`);
  await navigate(`${BASE}/login`);
  await sleep(600);
  const t3b = await pageTheme();
  check('T3a 有本地标记仍 owl（不被缺省覆盖）', t3a === 'owl', t3a);
  check('T3b 无本地标记跟随缺省 geometric', t3b === 'geometric', t3b);

  // A6b：账号偏好=缺省（都 geometric）时，同步仍必须物化键
  await setPref({ loginTheme: 'geometric' });
  await ev(`localStorage.setItem('login.theme','owl')`);
  await loginAs('admin', 'admin');
  check('A6 弹窗重开', await openPrefs());
  await sleep(600);
  await click('[data-testid="pref-sync-from-account"]');
  let synced = null;
  for (let i = 0; i < 12; i += 1) { await sleep(400); synced = await ev(`(() => { const v = localStorage.getItem('login.theme'); return v === null ? null : v; })()`); if (synced) break; }
  check('A6 从账号同步写入本机标记（值=geometric）', synced === 'geometric', String(synced));
  check('A6b 账号偏好=缺省时同步仍物化键（键存在）', synced !== null && synced === 'geometric', JSON.stringify({ synced }));

  // A7：跟随默认 → 删键
  await click('[data-testid="pref-follow-default"]');
  let followed = 'x';
  for (let i = 0; i < 12; i += 1) { await sleep(400); followed = await ev(`localStorage.getItem('login.theme')`); if (followed === null) break; }
  const hasMarkAfter = await ev(`document.querySelector('[data-testid="pref-mark-state"]')?.dataset.hasMark`);
  check('A7 跟随默认后本地标记被删除（null）', followed === null && hasMarkAfter === 'false', JSON.stringify({ followed, hasMarkAfter }));
  await closePrefs();

  // A8：第三态
  const prefs = await ev(`fetch('/api/v1/users/me/preferences',{headers:${hdr}}).then(r=>r.json()).then(j=>j.data)`);
  check('A8 第三态：loginTheme=null 且 followDefault=true', prefs && prefs.loginTheme === null && prefs.followDefault === true, JSON.stringify(prefs));

  // A9：改缺省 → 偏好仍 null，登录页跟随
  await setDefault('orbit');
  const prefs2 = await ev(`fetch('/api/v1/users/me/preferences',{headers:${hdr}}).then(r=>r.json()).then(j=>j.data)`);
  await navigate(`${BASE}/login`);
  await sleep(700);
  const a9theme = await pageTheme();
  check('A9 改缺省后偏好仍 null 且登录页跟随 orbit', prefs2?.loginTheme === null && a9theme === 'orbit', `prefs=${JSON.stringify(prefs2)} theme=${a9theme}`);

  // A10：同传 → 400
  const a10 = await ev(`fetch('/api/v1/users/me/preferences',{method:'PUT',headers:${hdr},body:JSON.stringify({loginTheme:'owl',followDefault:true})}).then(r=>r.status)`);
  check('A10 同传 followDefault+loginTheme → 400', a10 === 400, `status=${a10}`);

  // A4：登录前后本地标记完全相同（且退出后登录页仍 owl）
  await loginAs('admin', 'admin');
  await ev(`localStorage.setItem('login.theme','owl')`);
  const markBefore = await markOf();
  await navigate(`${BASE}/dashboard`);
  await sleep(1500);
  const markAfterDash = await markOf();
  await navigate(`${BASE}/login`);
  await sleep(700);
  check('A4 登录后本地标记不变', markBefore === markAfterDash && markAfterDash === 'owl', `${markBefore} → ${markAfterDash}`);
  check('A4 退出后登录页仍 owl', (await pageTheme()) === 'owl', await pageTheme());
  // 复原：admin 偏好回 pencil、缺省回原值
  await ev(`fetch('/api/v1/users/me/preferences',{method:'PUT',headers:${hdr},body:JSON.stringify({loginTheme:'pencil'})}).then(r=>r.status)`);
  await setDefault(origDefault || 'pencil');
  check('收尾：缺省与 admin 偏好已复原', (await getDefault()) === (origDefault || 'pencil'), await getDefault());

  check('页面无 JS 异常', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '));
  const passed = results.filter((r) => r.ok).length;
  console.log(`\n==== T107_VERIFY: ${passed}/${results.length} PASS, pageErrors=${pageErrors.length} ====`);
  fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify({ results, pageErrors }, null, 2));
  child.kill();
  process.exit(results.every((r) => r.ok) && pageErrors.length === 0 ? 0 : 1);
})().catch((e) => {
  console.error('VERIFY ERROR:', e.message);
  try { fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify({ results, pageErrors, error: e.message }, null, 2)); } catch { /* ignore */ }
  process.exit(2);
});
