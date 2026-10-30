// t47 最终页面补验：账号管理页、资产详情、任务负责人下拉、团队/公告写入口
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const API = `${BASE}/api/v1`;
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t47';
fs.mkdirSync(OUT, { recursive: true });
const PASS = 'Smoke@12345';
const results = {};
const errors = [];
const reqs = [];

const api = async (path, { method = 'GET', token, body } = {}) => {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: res.status, json };
};
const login = async (username, password) => {
  const res = await api('/auth/login', { method: 'POST', body: { username, password } });
  if (!res.json?.data?.token) throw new Error(`登录失败 ${username}: ${JSON.stringify(res.json)}`);
  return res.json.data;
};

const admin = await login('admin', 'admin');
const engineer = await login('smoke_engineer', PASS);
const leader = await login('smoke_leader', PASS);
console.log('[t47] tokens ready');

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 160)}`));
page.on('request', (r) => { if (r.url().includes('/api/')) reqs.push(`${r.method()} ${r.url().replace(BASE, '')}`); });

const doLogin = async (u, p) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder('用户名').fill(u);
  await page.getByPlaceholder('密码').fill(p);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
};
const logout = async () => { await page.evaluate(() => localStorage.clear()); await context.clearCookies(); };
const btn = async (name) => page.getByRole('button', { name }).count();

try {
  // ===== A) 工程师：账号管理页 =====
  await doLogin('smoke_engineer', PASS);
  const menuUsers = await page.getByRole('menuitem', { name: /账号管理/ }).count();
  await page.goto(`${BASE}/users`);
  await page.waitForTimeout(2500);
  const bodyText = await page.locator('body').innerText();
  const hasForbiddenText = /无权|403|没有权限|仅管理员|Forbidden/.test(bodyText);
  const userRows = await page.locator('.ant-table-row').count();
  const usersReq = reqs.filter((r) => /^GET \/api\/v1\/users(\?|$)/.test(r)).length;
  results.engineerUsersMenuHidden = menuUsers === 0;
  results.engineerUsersPageForbiddenOrEmpty = hasForbiddenText || userRows === 0;
  results.engineerUsersRequestCount = usersReq;
  await page.screenshot({ path: `${OUT}/A-engineer-users.png`, fullPage: true });

  // ===== B) 工程师：资产列表 → 详情不再 403 =====
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2200);
  const rowCount = await page.locator('.ant-table-row').count();
  results.engineerAssetListRows = rowCount;
  const engAssets = (await api('/assets?page=0&size=5', { token: engineer.token })).json?.data;
  const engList = engAssets?.content ?? engAssets?.items ?? engAssets?.records ?? [];
  results.engineerListApiCount = engList.length;
  if (engList.length > 0) {
    await page.goto(`${BASE}/assets/${engList[0].id}`);
    await page.waitForTimeout(2800);
    const detailText = await page.locator('body').innerText();
    results.engineerAssetDetailUrl = page.url();
    results.engineerAssetDetailNo403 = !/不存在或已被删除|无权访问|403|Forbidden/.test(detailText);
    results.engineerAssetDetailHasTabs = (await page.getByRole('tab').count()) > 0;
    results.engineerAssetDetailTitle = (await page.locator('.ant-card-head-title, h2, .ant-descriptions-item-label').first().innerText().catch(() => '')).slice(0, 40);
  } else {
    results.engineerAssetListEmpty = true;
  }
  await page.screenshot({ path: `${OUT}/B-engineer-asset-detail.png`, fullPage: true });

  // ===== C) 工程师：任务负责人下拉（目录接口） =====
  await page.goto(`${BASE}/tasks`);
  await page.waitForTimeout(2200);
  // 任务页任何「人员选择」下拉（筛选栏或新建弹窗）都应能通过 /users/directory 取数
  const pageSelects = page.locator('.ant-select');
  const pageSelCount = await pageSelects.count();
  results.taskPageSelectCount = pageSelCount;
  for (let i = 0; i < Math.min(pageSelCount, 6); i += 1) {
    await pageSelects.nth(i).click().catch(() => {});
    await page.waitForTimeout(600);
    const opts = await page.locator('.ant-select-item-option').count();
    if (opts > 0) { results.taskAssigneeOptionCount = opts; results.taskDropdownWorked = true; break; }
    await page.keyboard.press('Escape');
  }
  await page.keyboard.press('Escape');
  await page.screenshot({ path: `${OUT}/C-engineer-task-dropdown.png`, fullPage: true });
  // 目录接口直连兜底（页面下拉的数据源）
  const dir = await api('/users/directory?limit=5', { token: engineer.token });
  results.directoryStatus = dir.status;
  const dirBody = JSON.stringify(dir.json ?? {});
  results.directoryHasPii = /"email"|"phone"|"passwordHash"|"roles"/.test(dirBody);
  results.directoryItems = Array.isArray(dir.json?.data) ? dir.json.data.length : 0;

  // ===== D) 工程师：团队/公告写入口仍隐藏 =====
  await page.goto(`${BASE}/teams`);
  await page.waitForTimeout(2000);
  results.engineerTeamWriteHidden = (await btn(/创建团队/)) === 0 && (await btn(/^编\s*辑$/)) === 0;
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1800);
  await page.getByRole('tab', { name: /公\s*告/ }).click().catch(() => {});
  await page.waitForTimeout(1000);
  results.engineerAnnounceWriteHidden = (await btn(/发布公告/)) === 0;

  // ===== E) 管理员：账号管理页正常 + 团队/公告写入口在 =====
  await logout();
  await doLogin('admin', 'admin');
  await page.goto(`${BASE}/users`);
  await page.waitForTimeout(2500);
  results.adminUsersRows = await page.locator('.ant-table-row').count();
  await page.screenshot({ path: `${OUT}/D-admin-users.png`, fullPage: true });
  await page.goto(`${BASE}/teams`);
  await page.waitForTimeout(1800);
  results.adminTeamCreateVisible = (await btn(/创建团队/)) > 0;
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1800);
  await page.getByRole('tab', { name: /公\s*告/ }).click().catch(() => {});
  await page.waitForTimeout(1000);
  results.adminAnnounceWriteVisible = (await btn(/发布公告/)) > 0;

  // ===== F) 团队长：仍无新建/删除团队 =====
  await logout();
  await doLogin('smoke_leader', PASS);
  await page.goto(`${BASE}/teams`);
  await page.waitForTimeout(1800);
  results.leaderCreateHidden = (await btn(/创建团队/)) === 0;
  results.leaderDeleteHidden = (await btn(/^删\s*除$/)) === 0;
  await page.screenshot({ path: `${OUT}/E-leader-teams.png`, fullPage: true });
} finally {
  await page.screenshot({ path: `${OUT}/99-last.png`, fullPage: true }).catch(() => {});
  await browser.close();
}

results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t47.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const assetOk = results.engineerAssetListEmpty === true
  || (results.engineerAssetDetailNo403 === true && results.engineerAssetDetailHasTabs === true);
const ok =
  results.engineerUsersPageForbiddenOrEmpty === true &&
  assetOk &&
  results.directoryStatus === 200 && results.directoryHasPii === false && results.directoryItems > 0 &&
  (results.taskAssigneeOptionCount > 0 || results.taskPageSelectCount === 0) &&
  results.engineerTeamWriteHidden === true && results.engineerAnnounceWriteHidden === true &&
  results.adminUsersRows > 0 && results.adminTeamCreateVisible === true && results.adminAnnounceWriteVisible === true &&
  results.leaderCreateHidden === true && results.leaderDeleteHidden === true &&
  errors.length === 0;
console.log(ok ? 'T47_VERIFY: PASS' : 'T47_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
