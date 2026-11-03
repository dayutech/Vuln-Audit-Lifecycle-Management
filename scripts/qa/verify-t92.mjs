// t92 页面验证：菜单选中态 / 品牌区 / 模拟视角 / 工程师无入口
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t92';
fs.mkdirSync(OUT, { recursive: true });
const results = {};
const errors = [];

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 140)}`));

const login = async (u, p) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder(/用户名|Username/).fill(u);
  await page.getByPlaceholder(/密码|Password/).fill(p);
  await page.getByRole('button', { name: /登\s*录|Sign in|Login/i }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
  await page.waitForTimeout(1500);
};
const selectedText = async () => {
  const el = page.locator('.ant-menu-item-selected').first();
  return (await el.count()) ? (await el.innerText()).trim() : '';
};

await login('admin', 'admin');

// ===== 1) 菜单选中态：逐项点击 18 个 =====
const MENU = [
  ['/dashboard', /安全态势|仪表盘|Dashboard/],
  ['/customers', /客户管理|Customers/],
  ['/projects', /项目管理|Projects/],
  ['/assets', /产品与资产|Assets/],
  ['/asset-groups', /资产分组|Asset groups/],
  ['/vulnerabilities', /漏洞|Vulnerabilit/],
  ['/tasks', /任务|Tasks/],
  ['/approvals', /审批|Approvals/],
  ['/knowledge', /知识库|Knowledge/],
  ['/documents', /文档|Documents/],
  ['/tools', /工具|Tools/],
  ['/teams', /团队|Teams/],
  ['/approval-scopes', /审批范围|Approval scopes/],
  ['/users', /账号管理|用户管理|Users/],
  ['/roles', /角色|Roles/],
  ['/audit-logs', /审计|Audit/],
  ['/import', /导入|Import/],
  ['/notifications', /系统消息|消息|Notifications/],
];
const menuOk = [];
for (const [path, re] of MENU) {
  await page.goto(BASE + path);
  await page.waitForTimeout(1200);
  const text = await selectedText();
  menuOk.push({ path, matched: re.test(text), text });
}
results.menuChecked = menuOk.length;
results.menuFailed = menuOk.filter((m) => !m.matched).map((m) => `${m.path}→${m.text}`);

// ===== 2) 详情路由前缀高亮 =====
const detailChecks = [];
for (const [path, re] of [
  [`/customers/00000000-0000-4000-8000-000000000001`, /客户管理|Customers/],
  [`/projects/00000000-0000-4000-8000-000000000002`, /项目管理|Projects/],
  [`/assets/00000000-0000-4000-8000-000000000003`, /产品与资产|Assets/],
]) {
  await page.goto(BASE + path);
  await page.waitForTimeout(1500);
  const text = await selectedText();
  detailChecks.push({ path, matched: re.test(text), text });
}
results.detailRoutesChecked = detailChecks.length;
results.detailRoutesFailed = detailChecks.filter((d) => !d.matched).map((d) => `${d.path}→${d.text}`);
await page.screenshot({ path: `${OUT}/01-menu-detail.png`, fullPage: true });

// ===== 3) 品牌区 =====
await page.goto(`${BASE}/dashboard`);
await page.waitForTimeout(1500);
const brandText = await page.locator('.ant-layout-sider').first().innerText();
results.brandExpanded = /漏洞审计平台/.test(brandText);
await page.screenshot({ path: `${OUT}/02-brand-expanded.png`, fullPage: true });
// 折叠
const collapseBtn = page.locator('.ant-layout-sider-trigger').first();
if (await collapseBtn.count()) {
  await collapseBtn.click();
  await page.waitForTimeout(900);
}
const brandCollapsed = await page.locator('.ant-layout-sider').first().innerText();
results.brandCollapsedHidesName = !/漏洞审计平台/.test(brandCollapsed);
const logoCount = await page.locator('.ant-layout-sider svg').count();
results.brandLogoVisible = logoCount > 0;
await page.screenshot({ path: `${OUT}/03-brand-collapsed.png`, fullPage: true });
// 展开回来
if (await collapseBtn.count()) {
  await collapseBtn.click();
  await page.waitForTimeout(900);
}

// ===== 4) 模拟视角：入口可见 + 选择器可开；实际态用接口+storage 注入（选择器提交路径见 t91 E2E）=====
await page.locator('.ant-layout-header .ant-dropdown-trigger, .ant-layout-header [class*="avatar"]').first().click().catch(() => {});
await page.waitForTimeout(700);
let entry = page.locator('.ant-dropdown-menu-item', { hasText: /以其他用户视角查看|View as another user/ }).first();
if (!(await entry.count())) {
  await page.getByText('admin', { exact: false }).last().click().catch(() => {});
  await page.waitForTimeout(700);
  entry = page.locator('.ant-dropdown-menu-item', { hasText: /以其他用户视角查看|View as another user/ }).first();
}
results.adminEntryVisible = (await entry.count()) > 0;
if (results.adminEntryVisible) {
  await entry.click();
  await page.waitForTimeout(1500);
  results.pickerOpened = (await page.locator('.ant-modal-wrap, .ant-drawer-open').count()) > 0;
  await page.keyboard.press('Escape');
  await page.waitForTimeout(800);
  if (await page.locator('.ant-modal-wrap').count()) {
    await page.locator('.ant-modal-wrap button', { hasText: /取消|Cancel/i }).first().click().catch(() => {});
    await page.waitForTimeout(800);
  }
}

// 通过接口发起模拟并注入三键（模拟 t91 的 begin() 结果），然后整页重载
const adminToken = await page.evaluate(() => window.localStorage.getItem('access_token'));
const engInfo = await page.evaluate(async () => {
  const r = await fetch('/api/v1/users?page=0&size=50', { headers: { authorization: `Bearer ${window.localStorage.getItem('access_token')}` } });
  const j = await r.json();
  const c = j?.data?.content ?? [];
  const u = c.find((x) => x.username === 'smoke_engineer');
  return u ? { id: u.id, username: u.username } : null;
});
if (engInfo && adminToken) {
  const imp = await page.evaluate(async ({ id }) => {
    const token = window.localStorage.getItem('access_token');
    const r = await fetch('/api/v1/admin/impersonate', {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({ userId: id, reason: 't92 page verify' }),
    });
    return { status: r.status, body: await r.json() };
  }, { id: engInfo.id });
  results.impersonateApiStatus = imp.status;
  const data = imp.body?.data;
  if (data?.token) {
    await page.evaluate(({ original, target, impToken }) => {
      window.localStorage.setItem('access_token', impToken);
      window.localStorage.setItem('impersonation.original', JSON.stringify(original));
      window.localStorage.setItem('impersonation.target', JSON.stringify(target));
    }, {
      original: { token: adminToken, username: 'admin' },
      target: { id: engInfo.id, username: engInfo.username, roles: data.target?.roles ?? [], expiresAt: data.expiresAt },
      impToken: data.token,
    });
    await page.reload();
    await page.waitForTimeout(3500);
    const bodyAfter = await page.locator('body').innerText();
    results.bannerVisible = /正在以|Viewing as/.test(bodyAfter);
    results.bannerHasUsername = /smoke_engineer/.test(bodyAfter);
    results.bannerHasCountdown = /剩余|Expires in/.test(bodyAfter);
    results.engineerViewUsersHidden = !(await page.locator('.ant-menu-item', { hasText: /账号管理|用户管理|Users/ }).count());
    // 客户读全员开放 → 菜单仍在，但写入口（新建客户）应隐藏
    await page.goto(`${BASE}/customers`);
    await page.waitForTimeout(2500);
    results.engineerCustomerCreateHidden = (await page.getByRole('button', { name: /新建客户|新建|New customer/i }).count()) === 0;
    await page.goto(`${BASE}/dashboard`);
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${OUT}/04-impersonating.png`, fullPage: true });

    const exitBtn = page.locator('button', { hasText: /退出模拟|Exit impersonation/ }).first();
    results.exitButtonVisible = (await exitBtn.count()) > 0;
    if (results.exitButtonVisible) {
      await exitBtn.click();
      await page.waitForTimeout(4500);
    }
    await page.reload();
    await page.waitForTimeout(3500);
    const bodyAfterExit = await page.locator('body').innerText();
    results.bannerGoneAfterExit = !/正在以|Viewing as/.test(bodyAfterExit);
    results.adminViewRestored = (await page.locator('.ant-menu-item', { hasText: /账号管理|用户管理|Users/ }).count()) > 0;
    results.impersonationKeysCleared = await page.evaluate(() =>
      window.localStorage.getItem('impersonation.original') === null &&
      window.localStorage.getItem('impersonation.target') === null);
    await page.screenshot({ path: `${OUT}/05-after-exit.png`, fullPage: true });
  }
}
// ===== 5) English 品牌名 =====
await page.locator('[data-testid="lang-switcher"]').first().click();
await page.waitForTimeout(400);
await page.locator('.ant-dropdown-menu-item').filter({ hasText: /English/ }).first().click();
await page.waitForTimeout(1200);
const siderEn = await page.locator('.ant-layout-sider').first().innerText();
results.brandEnglish = /Vuln Audit/.test(siderEn);
// 切回中文
await page.locator('[data-testid="lang-switcher"]').first().click();
await page.waitForTimeout(400);
await page.locator('.ant-dropdown-menu-item').filter({ hasText: /中文|Chinese/ }).first().click();
await page.waitForTimeout(1000);

// ===== 6) 工程师登录：无模拟入口 =====
const ctx2 = await browser.newContext({ viewport: { width: 1400, height: 900 } });
const page2 = await ctx2.newPage();
page2.on('pageerror', (e) => errors.push(`pageerror-eng: ${e.message.slice(0, 140)}`));
await page2.goto(`${BASE}/login`);
await page2.getByPlaceholder(/用户名|Username/).fill('smoke_engineer');
await page2.getByPlaceholder(/密码|Password/).fill('Smoke@12345');
await page2.getByRole('button', { name: /登\s*录|Sign in/i }).click();
await page2.waitForURL('**/dashboard', { timeout: 30000 });
await page2.waitForTimeout(1500);
await page2.locator('.ant-layout-header .ant-dropdown-trigger, .ant-layout-header [class*="avatar"]').first().click().catch(() => {});
await page2.waitForTimeout(800);
const engMenuText = await page2.locator('.ant-dropdown').last().innerText().catch(() => '');
results.engineerNoImpersonationEntry = !/以其他用户视角查看|View as another user/.test(engMenuText);
await page2.screenshot({ path: `${OUT}/06-engineer-menu.png`, fullPage: true });
await ctx2.close();

await browser.close();
results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t92.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const ok =
  results.menuChecked >= 15 &&
  results.menuFailed.length === 0 &&
  results.detailRoutesFailed.length === 0 &&
  results.brandExpanded === true &&
  results.brandLogoVisible === true &&
  results.adminEntryVisible === true &&
  results.bannerVisible === true &&
  results.bannerHasUsername === true &&
  results.bannerHasCountdown === true &&
  results.engineerViewUsersHidden === true &&
  results.engineerCustomerCreateHidden === true &&
  results.bannerGoneAfterExit === true &&
  results.adminViewRestored === true &&
  results.impersonationKeysCleared === true &&
  results.brandEnglish === true &&
  results.engineerNoImpersonationEntry === true &&
  errors.length === 0;
console.log(ok ? 'T92_VERIFY: PASS' : 'T92_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
