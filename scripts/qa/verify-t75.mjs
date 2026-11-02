// t75 页面验证：客户管理 / 项目管理 / 资产详情所属项目页签 / 资产列表客户·项目列 / 仪表盘分布卡片 + English
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const API = `${BASE}/api/v1`;
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t75';
fs.mkdirSync(OUT, { recursive: true });
const results = {};
const errors = [];
const S = String(Date.now()).slice(-6);

const api = async (path, { method = 'GET', token, body } = {}) => {
  const res = await fetch(API + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: res.status, json };
};
const login = async (u, p) => {
  const r = await api('/auth/login', { method: 'POST', body: { username: u, password: p } });
  if (!r.json?.data?.token) throw new Error(`login fail ${u}`);
  return r.json.data;
};
const admin = await login('admin', 'admin');
const eng = await login('smoke_engineer', 'Smoke@12345');

// ===== 夹具：客户 → 分期项目 → 两期 → 资产（带 projectId+phaseId）=====
const cust = await api('/customers', { method: 'POST', token: admin.token, body: { code: `T75-CUST-${S}`, name: `T75客户-${S}`, industry: '互联网', level: 'A' } });
const custId = cust.json?.data?.id;
const proj = await api('/projects', { method: 'POST', token: admin.token, body: { code: `T75-PRJ-${S}`, name: `T75项目-${S}`, customerId: custId, managerId: admin.userId, type: 'PENTEST', isPhased: true } });
const projId = proj.json?.data?.id;
const ph1 = await api(`/projects/${projId}/phases`, { method: 'POST', token: admin.token, body: { name: `一期-${S}`, startDate: '2026-01-01', endDate: '2026-03-31' } });
const phaseId = ph1.json?.data?.id;
const team = (await api('/teams?page=0&size=1', { token: admin.token })).json?.data?.content?.[0]?.id;
const asset = await api('/assets', { method: 'POST', token: admin.token, body: { name: `T75资产-${S}`, targetType: 'Web应用', targetSource: '客户委托', priority: 'P2', status: 'active', teamId: team, projectId: projId, phaseId, linkType: 'TARGET' } });
const assetId = asset.json?.data?.id;
results.fixtures = { custId, projId, phaseId, assetId, custStatus: cust.status, projStatus: proj.status, assetStatus: asset.status };

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 160)}`));

const setLang = async (re) => {
  await page.locator('[data-testid="lang-switcher"]').first().click();
  await page.waitForTimeout(400);
  await page.locator('.ant-dropdown-menu-item').filter({ hasText: re }).first().click();
  await page.waitForTimeout(900);
};
const doLogin = async (u, p) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder(/用户名|Username/).fill(u);
  await page.getByPlaceholder(/密码|Password/).fill(p);
  await page.getByRole('button', { name: /登\s*录|Sign in|Login/i }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
  await page.waitForTimeout(1200);
};
const text = async () => page.locator('body').innerText();

try {
  await doLogin('admin', 'admin');

  // ===== 1) 客户管理 =====
  await page.goto(`${BASE}/customers`);
  await page.waitForTimeout(2500);
  let t = await text();
  results.custListTitle = /客户|Customers/i.test(t);
  results.custRowVisible = (await page.locator('.ant-table-row', { hasText: `T75客户-${S}` }).count()) > 0;
  results.custCreateBtn = (await page.getByRole('button', { name: /新建客户|新建|New customer/i }).count()) > 0;
  // 详情抽屉
  await page.locator('.ant-table-row', { hasText: `T75客户-${S}` }).first().getByText(/详情|Detail/i).first().click();
  await page.waitForTimeout(2000);
  const dtext = await page.locator('.ant-drawer-open').last().innerText().catch(() => '');
  results.custDetailHasProjects = /项目|Projects/i.test(dtext);
  results.custDetailHasStats = /资产|Assets/i.test(dtext);
  await page.keyboard.press('Escape');
  await page.screenshot({ path: `${OUT}/01-customers.png`, fullPage: true });

  // ===== 2) 项目管理（期次 / 状态机 / 资产清单）=====
  await page.goto(`${BASE}/projects`);
  await page.waitForTimeout(2500);
  t = await text();
  results.projListVisible = (await page.locator('.ant-table-row', { hasText: `T75项目-${S}` }).count()) > 0;
  await page.locator('.ant-table-row', { hasText: `T75项目-${S}` }).first().getByText(/详情|Detail/i).first().click();
  await page.waitForTimeout(2500);
  const ptext = await text();
  results.projDetailPhases = /一期-/.test(ptext);
  results.projDetailStateButtons = /启动项目|Start|暂停|Pause|关闭|Close/i.test(ptext);
  results.projDetailAssets = /资产|Assets/i.test(ptext);
  await page.screenshot({ path: `${OUT}/02-project-detail.png`, fullPage: true });

  // ===== 3) 资产详情「所属项目」页签 =====
  await page.goto(`${BASE}/assets/${assetId}`);
  await page.waitForTimeout(2500);
  const tabTexts = await page.getByRole('tab').allInnerTexts().catch(() => []);
  results.assetTabs = tabTexts.join('|').slice(0, 200);
  const projTab = page.getByRole('tab', { name: /所属项目|Linked projects/i }).first();
  results.assetProjectTabExists = (await projTab.count()) > 0;
  if (results.assetProjectTabExists) {
    await projTab.click();
    await page.waitForTimeout(1800);
    const atext = await text();
    results.assetProjectLinkRow = new RegExp(`T75项目|T75客户|一期-`).test(atext);
  }
  await page.screenshot({ path: `${OUT}/03-asset-project-tab.png`, fullPage: true });

  // ===== 4) 资产列表客户/项目列 + 筛选 =====
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2500);
  t = await text();
  results.assetListCustomerCol = /客户|Customer/i.test(t);
  results.assetListProjectCol = /项目|Project/i.test(t);
  results.assetListRowVisible = (await page.locator('.ant-table-row', { hasText: `T75资产-${S}` }).count()) > 0;
  await page.screenshot({ path: `${OUT}/04-asset-list.png`, fullPage: true });

  // ===== 5) 仪表盘两张分布卡片 =====
  await page.goto(`${BASE}/dashboard`);
  await page.waitForTimeout(3500);
  t = await text();
  results.dashCustomerCard = /按客户分布|By customer/i.test(t);
  results.dashProjectCard = /按项目分布|By project/i.test(t);
  await page.screenshot({ path: `${OUT}/05-dashboard-cards.png`, fullPage: true });

  // ===== 6) English：新增页面文案 =====
  await setLang(/English/);
  await page.goto(`${BASE}/customers`);
  await page.waitForTimeout(2200);
  results.enCustomers = /Customer|Industry|Level/i.test(await text());
  await page.goto(`${BASE}/projects`);
  await page.waitForTimeout(2200);
  results.enProjects = /Project|Customer|Manager|Status/i.test(await text());
  await setLang(/中文|Chinese/);
  await page.goto(`${BASE}/customers`);
  await page.waitForTimeout(2000);
  results.zhRestored = /客户/.test(await text());
  await page.screenshot({ path: `${OUT}/06-customers-zh.png`, fullPage: true });
} finally {
  await page.screenshot({ path: `${OUT}/99-last.png`, fullPage: true }).catch(() => {});
  await browser.close();
}

results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t75.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const ok =
  results.fixtures?.custStatus && results.fixtures?.projStatus && results.fixtures?.assetStatus &&
  results.custListTitle && results.custRowVisible && results.custCreateBtn &&
  results.custDetailHasProjects && results.custDetailHasStats &&
  results.projListVisible && results.projDetailPhases && results.projDetailStateButtons && results.projDetailAssets &&
  results.assetProjectTabExists && results.assetProjectLinkRow &&
  results.assetListCustomerCol && results.assetListProjectCol && results.assetListRowVisible &&
  results.dashCustomerCard && results.dashProjectCard &&
  results.enCustomers && results.enProjects && results.zhRestored &&
  errors.length === 0;
console.log(ok ? 'T75_VERIFY: PASS' : 'T75_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
