/**
 * t98 页面验证：登录页主题（8 套）+ 四类交互 + 持久化 + 英文文案
 *
 * 方式：msedge --headless=new + 原生 CDP（Node 22 内置 WebSocket，零新依赖）
 * 目标：线上站点 http://192.168.188.200:8089（nginx 反代到后端 8088）
 */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const DBG_PORT = 9444;
const BASE = process.env.BASE_URL || 'http://192.168.188.200:8089';
const OUT = __dirname;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const pageErrors = [];
let ws;
let nextId = 1;
const pending = new Map();

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail === undefined ? '' : `  :: ${detail}`}`);
}

function send(method, params = {}) {
  const id = nextId++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}

async function waitDebugPort() {
  for (let i = 0; i < 80; i += 1) {
    try {
      const res = await fetch(`http://127.0.0.1:${DBG_PORT}/json/version`);
      if (res.ok) return res.json();
    } catch { /* not up yet */ }
    await sleep(250);
  }
  throw new Error('devtools port not ready');
}

async function connect() {
  const targets = await (await fetch(`http://127.0.0.1:${DBG_PORT}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page');
  if (!page) throw new Error('no page target');
  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(JSON.stringify(msg.error)));
      else resolve(msg.result);
      return;
    }
    if (msg.method === 'Runtime.exceptionThrown') {
      pageErrors.push(msg.params?.exceptionDetails?.text ?? 'exception');
    } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params?.type === 'error') {
      const args = (msg.params.args || []).map((a) => a.value ?? a.description ?? '').join(' ');
      if (args && !/favicon|Download the React DevTools/i.test(args)) pageErrors.push(args.slice(0, 160));
    }
  };
}

async function evaluate(expression) {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) throw new Error(`eval failed: ${r.exceptionDetails.text} ${r.exceptionDetails.exception?.description ?? ''}`);
  return r.result.value;
}

async function waitFor(expression, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try { if (await evaluate(expression)) return true; } catch { /* loading */ }
    await sleep(120);
  }
  return false;
}

async function navigate(url) {
  await send('Page.navigate', { url });
  await waitFor(`document.readyState === 'complete'`);
  await sleep(500);
}

async function viewport(width, height) {
  await send('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: 1, mobile: false, screenWidth: width, screenHeight: height,
  });
}

async function screenshot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(OUT, name), Buffer.from(data, 'base64'));
}

async function moveMouse(x, y) {
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, button: 'none', buttons: 0, clickCount: 0 });
  await sleep(60);
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: x + 5, y: y + 5, button: 'none', buttons: 0, clickCount: 0 });
  await sleep(320);
}

async function click(selector) {
  const box = await evaluate(`(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  })()`);
  if (!box) return false;
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', {
      type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1,
    });
  }
  await sleep(350);
  return true;
}

async function clickText(textRe) {
  const box = await evaluate(`(() => {
    const re = new RegExp(${JSON.stringify(textRe)});
    const els = [...document.querySelectorAll('button, .ant-dropdown-menu-item, li, a, div')];
    const el = els.find((e) => re.test((e.textContent || '').trim()) && e.getBoundingClientRect().width > 0);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  })()`);
  if (!box) return false;
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', {
      type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1,
    });
  }
  await sleep(450);
  return true;
}

async function typeInto(selector, text) {
  await click(selector);
  await send('Input.insertText', { text });
  await sleep(150);
}

async function clickMenuItem(textRe) {
  const box = await evaluate(`(() => {
    const re = new RegExp(${JSON.stringify(textRe)});
    const items = [...document.querySelectorAll('[role="menuitem"], .ant-dropdown-menu-item')];
    const el = items.find((e) => re.test((e.innerText || '').trim()) && e.getBoundingClientRect().width > 0);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2, txt: el.innerText.trim() };
  })()`);
  if (!box) return false;
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y, button: 'none' });
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', {
      type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1,
    });
  }
  await sleep(600);
  return true;
}

async function openUserMenu() {
  // 头部有两个 ant-dropdown-trigger：语言切换按钮 + 用户菜单（Avatar + 用户名）
  const box = await evaluate(`(() => {
    const triggers = [...document.querySelectorAll('.ant-layout-header .ant-dropdown-trigger')];
    const el = triggers.find((e) => e.querySelector('.ant-avatar')) || triggers[triggers.length - 1];
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  })()`);
  if (!box) return false;
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y, button: 'none' });
  for (const type of ['mousePressed', 'mouseReleased']) {
    await send('Input.dispatchMouseEvent', {
      type, x: box.x, y: box.y, button: 'left', buttons: type === 'mousePressed' ? 1 : 0, clickCount: 1,
    });
  }
  await sleep(400);
  return true;
}

const THEMES = ['pencil', 'geometric', 'owl', 'pixel-eyes'];
const ABSTRACT = ['aurora', 'mono-grid', 'orbit', 'big-type'];
const ALL = [...THEMES, ...ABSTRACT];
const ABSTRACT_BG = {
  aurora: 'rgb(10, 12, 17)',
  'mono-grid': 'rgb(244, 245, 247)',
  orbit: 'rgb(15, 18, 24)',
  'big-type': 'rgb(244, 242, 238)',
};
const NAMES_ZH = {
  pencil: '手绘小人', geometric: '几何小人', owl: '夜猫子', 'pixel-eyes': '像素脸',
};

const REACT_SET = `(sel, val) => {
  const el = document.querySelector(sel);
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(el, val);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
}`;

async function loginAdmin() {
  await navigate(`${BASE}/login`);
  await waitFor(`!!document.querySelector('#login_username')`);
  await evaluate(`(${REACT_SET})('#login_username', 'admin')`);
  await evaluate(`(${REACT_SET})('#login_password', 'admin')`);
  await sleep(250);
  const status = await evaluate(`(() => {
    const u = document.querySelector('#login_username').value;
    const p = document.querySelector('#login_password').value;
    return u + '/' + p.length;
  })()`);
  await click('button[type="submit"]');
  const ok = await waitFor(`location.pathname.startsWith('/dashboard')`, 20000);
  await sleep(1200);
  if (!ok) pageErrors.push(`login failed (fields=${status}) at ${await evaluate('location.pathname')}`);
  return ok;
}

async function themeOf() {
  return evaluate(`document.querySelector('[data-testid="login-character"]')?.dataset.theme ?? ''`);
}

async function main() {
  const args = [
    `--remote-debugging-port=${DBG_PORT}`,
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--user-data-dir=' + path.join(OUT, 'edge-profile-' + Date.now()),
    'about:blank',
  ];
  const child = spawn(EDGE, args, { stdio: 'ignore' });
  await waitDebugPort();
  await connect();
  await send('Page.enable');
  await send('Runtime.enable');
  await viewport(1440, 900);

  // ---------- 1) 登录 + 偏好弹窗 ----------
  const loggedIn = await loginAdmin();
  check('登录成功并进入仪表盘（新 dist 生效）', loggedIn, await evaluate('location.pathname'));

  const triggerSel = '.ant-dropdown-trigger';
  await openUserMenu();
  const menuOpened = await waitFor(`!!document.querySelector('.ant-dropdown-menu')`);
  check('用户菜单可展开', menuOpened);
  await clickMenuItem('偏好设置|Preferences');
  const modalOpen = await waitFor(`!!document.querySelector('[data-testid="login-theme-preference"]')`, 8000);
  check('偏好设置弹窗打开', modalOpen);

  const cards = await evaluate(`[...document.querySelectorAll('[data-testid^="theme-card-"]')].map(e => e.dataset.testid.replace('theme-card-',''))`);
  check('主题卡片 8 张（4 角色 + 4 抽象）', cards.length === 8 && ALL.every((t) => cards.includes(t)), cards.join(','));
  const groups = await evaluate(`[...document.querySelectorAll('[data-testid^="pref-group-"]')].map(e => e.dataset.testid)`);
  check('分组为角色 / 极简两组', groups.length === 2 && groups.join(',').includes('pref-group-character') && groups.join(',').includes('pref-group-abstract'), groups.join(','));
  const thumbs = await evaluate(`[...document.querySelectorAll('.theme-thumb')].filter(e => e.getBoundingClientRect().width > 0).length`);
  check('8 张缩略图渲染', thumbs === 8, String(thumbs));

  // 选择 owl
  await click('[data-testid="theme-card-owl"]');
  const localTheme = await waitFor(`localStorage.getItem('login.theme') === 'owl'`, 8000).then(async () => evaluate(`localStorage.getItem('login.theme')`));
  check('选择 owl → localStorage 落盘', localTheme === 'owl', String(localTheme));
  const serverTheme = await evaluate(`fetch('/api/v1/users/me/preferences',{headers:{Authorization:'Bearer '+localStorage.getItem('access_token')}}).then(r=>r.json()).then(j=>j.data?.loginTheme)`);
  check('选择 owl → 服务端落库', serverTheme === 'owl', String(serverTheme));
  const currentTag = await evaluate(`document.querySelector('[data-testid="pref-current-theme"]')?.textContent?.trim()`);
  check('当前主题 Tag 跟随', /owl|夜猫子/i.test(currentTag || ''), currentTag);
  await screenshot('shot-pref-modal-zh.png');

  // ---------- 2) 持久化 A：退出 → 登录页为 owl ----------
  await click('[data-testid="pref-close"]');
  await sleep(400);
  await openUserMenu();
  await waitFor(`!!document.querySelector('.ant-dropdown-menu')`);
  await clickMenuItem('退出登录|退出|Logout|Sign out');
  await waitFor(`location.pathname === '/login'`, 12000);
  await sleep(900);
  check('退出登录后登录页主题为 owl（本地缓存）', (await themeOf()) === 'owl', await themeOf());

  // ---------- 3) 持久化 B：清空 localStorage → 默认 pencil；登录后回到 owl（服务端） ----------
  await evaluate(`localStorage.removeItem('login.theme'); localStorage.removeItem('access_token'); localStorage.removeItem('user')`);
  await navigate(`${BASE}/login`);
  await sleep(700);
  check('清空缓存后登录页回落默认 pencil', (await themeOf()) === 'pencil', await themeOf());

  const relogin = await loginAdmin();
  const afterLoginCache = await evaluate(`localStorage.getItem('login.theme')`);
  check('重新登录后缓存被服务端值纠正为 owl', relogin && afterLoginCache === 'owl', `login=${relogin} cache=${afterLoginCache}`);
  await navigate(`${BASE}/login`);
  await sleep(900);
  check('再次进入登录页仍为 owl（服务端生效，跨设备）', (await themeOf()) === 'owl', await themeOf());

  // ---------- 4) 8 套主题渲染 ----------
  const render = [];
  for (const id of ALL) {
    await navigate(`${BASE}/login?theme=${id}`);
    await sleep(650);
    const info = await evaluate(`(() => {
      const wrap = document.querySelector('[data-testid="login-character"]');
      const svg = document.querySelector('[data-testid="login-char-svg"]');
      const abs = document.querySelector('[data-testid="login-abstract"]');
      return {
        theme: wrap?.dataset.theme ?? '',
        group: wrap?.dataset.group ?? '',
        hasSvg: !!svg,
        ariaHidden: svg ? svg.getAttribute('aria-hidden') : null,
        hasAbstract: !!abs,
        absBg: abs ? getComputedStyle(abs).backgroundColor : '',
        bg: getComputedStyle(document.body).backgroundColor,
        overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      };
    })()`);
    const ok = info.theme === id
      && (THEMES.includes(id) ? info.hasSvg && info.ariaHidden === 'true' && !info.hasAbstract : info.hasAbstract && !info.hasSvg)
      && info.overflowX <= 1;
    render.push({ id, ok, ...info });
    check(`主题 ${id} 渲染`, ok, `group=${info.group} svg=${info.hasSvg} abstract=${info.hasAbstract} overflowX=${info.overflowX}`);
    if (ABSTRACT.includes(id)) {
      check(`主题 ${id} 底色符合设计`, info.absBg === ABSTRACT_BG[id], `${info.absBg}（期望 ${ABSTRACT_BG[id]}）`);
    }
    if (id === 'pencil' || id === 'aurora') await screenshot(`shot-theme-${id}-1440.png`);
  }
  check('8 套主题全部渲染无兜底', render.every((r) => r.ok), render.filter((r) => !r.ok).map((r) => r.id).join(','));

  // ---------- 5) 眼睛跟随（pencil） ----------
  await navigate(`${BASE}/login?theme=pencil`);
  await sleep(700);
  const pupilAt = async (x, y) => {
    await moveMouse(x, y);
    return evaluate(`(() => {
      const el = document.querySelector('[data-testid="login-char-pupil"]');
      if (!el) return null;
      const m = new DOMMatrixReadOnly(getComputedStyle(el).transform === 'none' ? el.style.transform : getComputedStyle(el).transform);
      return { x: Number(m.e) || 0, y: Number(m.f) || 0 };
    })()`);
  };
  const right = await pupilAt(1380, 450);
  await screenshot('shot-eye-right.png');
  const left = await pupilAt(40, 450);
  await screenshot('shot-eye-left.png');
  const down = await pupilAt(700, 860);
  await screenshot('shot-eye-down.png');
  check('瞳孔向右偏移（x>0）', right && right.x > 0.5, JSON.stringify(right));
  check('瞳孔向左偏移（x<0）', left && left.x < -0.5, JSON.stringify(left));
  check('左右方向相反', right && left && Math.sign(right.x) !== Math.sign(left.x), `right=${right?.x} left=${left?.x}`);
  check('瞳孔向下偏移（y>0）', down && down.y > 0.5, JSON.stringify(down));

  // ---------- 6) 密码遮挡 ----------
  await evaluate(`document.querySelector('input[type=password]')?.focus()`);
  await sleep(500);
  const shielded = await evaluate(`!!document.querySelector('[data-testid="login-char-svg"]')?.classList.contains('is-shielding')`);
  const shieldedStyle = await evaluate(`(() => {
    const t = document.querySelector('[data-testid="login-character"]')?.dataset.theme;
    const map = { pencil: '.pencil-arm, .pencil-head', geometric: '.geo-head, .geo-eye-group', owl: '.owl-wing', 'pixel-eyes': '.pixel-face' };
    const el = document.querySelector(map[t] || 'svg');
    if (!el) return null;
    const cs = getComputedStyle(el);
    return { transform: cs.transform, opacity: cs.opacity };
  })()`);
  await screenshot('shot-pencil-password-focus.png');
  await evaluate(`document.querySelector('input[type=password]')?.blur()`);
  await sleep(400);
  const unshielded = await evaluate(`!!document.querySelector('[data-testid="login-char-svg"]')?.classList.contains('is-shielding')`);
  check('聚焦密码框 → is-shielding（角色遮挡）', shielded, JSON.stringify(shieldedStyle));
  check('失焦后遮挡解除', shielded && !unshielded, `focus=${shielded} blur=${unshielded}`);

  // ---------- 7) 眨眼（6–10s 内至少一次） ----------
  await navigate(`${BASE}/login?theme=pencil`);
  await sleep(600);
  let blinks = 0;
  let prev = false;
  const t0 = Date.now();
  while (Date.now() - t0 < 12000) {
    const now = await evaluate(`!!document.querySelector('[data-testid="login-char-svg"]')?.classList.contains('is-blinking')`);
    if (now && !prev) blinks += 1;
    prev = now;
    await sleep(45);
  }
  check('12s 内至少眨眼一次', blinks >= 1, `blinks=${blinks} window=12s`);

  // ---------- 8) prefers-reduced-motion ----------
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await navigate(`${BASE}/login?theme=pencil`);
  await sleep(800);
  const reduced = await evaluate(`(() => {
    const bob = document.querySelector('.char-bob');
    const pupil = document.querySelector('[data-testid="login-char-pupil"]');
    return {
      bobAnim: bob ? getComputedStyle(bob).animationName : 'none-element',
      pupilTransition: pupil ? getComputedStyle(pupil).transitionDuration : 'none-element',
    };
  })()`);
  check('减少动效：呼吸动画关闭', reduced.bobAnim === 'none', JSON.stringify(reduced));
  check('减少动效：瞳孔过渡时长 0s', /^0s/.test(reduced.pupilTransition || ''), reduced.pupilTransition);
  await screenshot('shot-pencil-reduced.png');
  await send('Emulation.setEmulatedMedia', { features: [] });

  // ---------- 9) 窄屏（375px）不重叠 ----------
  const narrow = [];
  for (const id of ALL) {
    await viewport(375, 812);
    await navigate(`${BASE}/login?theme=${id}`);
    await sleep(650);
    const geo = await evaluate(`(() => {
      const brand = document.querySelector('.login-hero__brand');
      const card = document.querySelector('.login-form-panel, form');
      const b = brand ? brand.getBoundingClientRect() : null;
      const c = card ? card.getBoundingClientRect() : null;
      return {
        overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        brandBottom: b ? Math.round(b.bottom) : null,
        cardTop: c ? Math.round(c.top) : null,
      };
    })()`);
    const ok = geo.overflowX <= 1 && geo.brandBottom !== null && geo.cardTop !== null && geo.brandBottom <= geo.cardTop + 2;
    narrow.push({ id, ok, ...geo });
    check(`窄屏 375 不重叠（${id}）`, ok, `brandBottom=${geo.brandBottom} cardTop=${geo.cardTop} overflowX=${geo.overflowX}`);
  }
  if (ALL.length) await screenshot('shot-narrow-375.png');
  await viewport(1440, 900);

  // ---------- 10) 英文文案 ----------
  await evaluate(`localStorage.setItem('app.lang','en-US'); localStorage.setItem('login.theme','build-not-a-theme'); localStorage.removeItem('login.theme')`);
  await navigate(`${BASE}/login?theme=owl`);
  await sleep(900);
  const en = await evaluate(`(() => {
    const txt = document.body.innerText;
    const cjk = (txt.match(/[\\u4e00-\\u9fa5]/g) || []).length;
    return { cjk, sample: txt.replace(/\\s+/g,' ').slice(0, 160) };
  })()`);
  check('英文登录页无中文残留', en.cjk === 0, `cjk=${en.cjk} :: ${en.sample}`);
  const enLogin = await loginAdmin();
  await openUserMenu();
  await waitFor(`!!document.querySelector('.ant-dropdown-menu')`);
  await clickMenuItem('Preferences|偏好设置');
  await waitFor(`!!document.querySelector('[data-testid="login-theme-preference"]')`, 8000);
  await sleep(500);
  const enModal = await evaluate(`(() => {
    const el = document.querySelector('[data-testid="login-theme-preference"]');
    const txt = el ? el.innerText : '';
    return { exists: !!el, cjk: (txt.match(/[\\u4e00-\\u9fa5]/g) || []).length, sample: txt.replace(/\\s+/g,' ').slice(0, 220) };
  })()`);
  check('英文偏好弹窗无中文残留', enLogin && enModal.exists && enModal.cjk === 0, `exists=${enModal.exists} cjk=${enModal.cjk} :: ${enModal.sample}`);
  await screenshot('shot-pref-modal-en.png');
  await click('[data-testid="pref-close"]');
  await evaluate(`localStorage.setItem('app.lang','zh-CN')`);

  // ---------- 11) 收尾：主题恢复 pencil ----------
  const putSt = await evaluate(`fetch('/api/v1/users/me/preferences',{method:'PUT',headers:{'Content-Type':'application/json',Authorization:'Bearer '+localStorage.getItem('access_token')},body:JSON.stringify({loginTheme:'pencil'})}).then(r=>r.status)`);
  await sleep(400);
  const restored = await evaluate(`fetch('/api/v1/users/me/preferences',{headers:{Authorization:'Bearer '+localStorage.getItem('access_token')}}).then(r=>r.json()).then(j=>j.data?.loginTheme).catch(()=> 'err')`);
  check('收尾：admin 主题恢复 pencil', restored === 'pencil', `PUT=${putSt} read=${restored}`);

  check('页面无 JS 异常', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '));

  const passed = results.filter((r) => r.ok).length;
  console.log(`\n==== T98_VERIFY: ${passed}/${results.length} PASS, pageErrors=${pageErrors.length} ====`);
  fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify({ results, pageErrors }, null, 2));
  child.kill();
  process.exit(results.every((r) => r.ok) && pageErrors.length === 0 ? 0 : 1);
}

main().catch(async (e) => {
  console.error('VERIFY ERROR:', e.message);
  try { fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify({ results, pageErrors, error: e.message }, null, 2)); } catch { /* ignore */ }
  process.exit(2);
});
