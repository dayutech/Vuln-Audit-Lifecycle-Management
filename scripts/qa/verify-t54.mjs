// t54 页面验证：三账号入口可见性（t52 口径）+ 中英切换（t53 成果）
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t54';
fs.mkdirSync(OUT, { recursive: true });
const PASS = 'Smoke@12345';
const results = {};
const errors = [];

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 160)}`));

const doLogin = async (u, p) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder(/用户名|Username/).fill(u);
  await page.getByPlaceholder(/密码|Password/).fill(p);
  await page.getByRole('button', { name: /登\s*录|Sign in|Login/i }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
  await page.waitForTimeout(1500);
};
const logout = async () => { await page.evaluate(() => localStorage.removeItem('token')); await context.clearCookies(); };
const btnCount = async (re) => page.getByRole('button', { name: re }).count();

try {
  // ===== 工程师 =====
  await doLogin('smoke_engineer', PASS);
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2200);
  results.engineerAssetsCreate = await btnCount(/新建资产|创建资产/);
  await page.goto(`${BASE}/asset-groups`);
  await page.waitForTimeout(2000);
  results.engineerGroupsCreate = await btnCount(/新建分组|创建分组/);
  await page.goto(`${BASE}/tasks`);
  await page.waitForTimeout(2000);
  results.engineerTasksCreate = await btnCount(/新建任务|创建任务/);
  await page.goto(`${BASE}/tools`);
  await page.waitForTimeout(2200);
  const uploadBtn = page.getByRole('button', { name: /上传工具|上传/ }).first();
  results.engineerToolsUploadExists = await uploadBtn.count();
  results.engineerToolsUploadDisabled = (await uploadBtn.count()) ? await uploadBtn.isDisabled() : null;
  results.engineerToolsDelete = await page.locator('a', { hasText: '删除' }).count();
  await page.screenshot({ path: `${OUT}/01-engineer-tools.png`, fullPage: true });

  // ===== 团队长 =====
  await logout();
  await doLogin('smoke_leader', PASS);
  await page.goto(`${BASE}/tasks`);
  await page.waitForTimeout(2000);
  results.leaderTasksCreate = await btnCount(/新建任务|创建任务/);
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2200);
  results.leaderAssetsCreate = await btnCount(/新建资产|创建资产/);
  await page.goto(`${BASE}/tools`);
  await page.waitForTimeout(2200);
  const leaderUpload = page.getByRole('button', { name: /上传工具|上传/ }).first();
  results.leaderToolsUploadDisabled = (await leaderUpload.count()) ? await leaderUpload.isDisabled() : null;
  results.leaderToolsDelete = await page.locator('a', { hasText: '删除' }).count();
  await page.screenshot({ path: `${OUT}/02-leader-tools.png`, fullPage: true });

  // ===== 管理员 =====
  await logout();
  await doLogin('admin', 'admin');
  // 保证列表里有样本（冒烟清理后工具可能为空）
  const loginRes = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin' }),
  });
  const adminTok = (await loginRes.json())?.data?.token;
  const toolCreated = await fetch(`${BASE}/api/v1/tools`, {
    method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${adminTok}` },
    body: JSON.stringify({ name: `t54-验证工具-${Date.now()}`, category: 'scanner', description: 't54 页面验证，用完删除' }),
  });
  const toolId = (await toolCreated.json())?.data?.id;
  await page.goto(`${BASE}/tools`);
  await page.waitForTimeout(2500);
  results.adminToolsRows = await page.locator('.ant-table-row').count();
  const adminUpload = page.getByRole('button', { name: /上传工具|上传/ }).first();
  results.adminToolsUploadDisabled = (await adminUpload.count()) ? await adminUpload.isDisabled() : null;
  results.adminToolsDelete = await page.locator('a', { hasText: '删除' }).count();
  if (toolId) {
    await fetch(`${BASE}/api/v1/tools/${toolId}`, { method: 'DELETE', headers: { authorization: `Bearer ${adminTok}` } }).catch(() => {});
  }
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2000);
  results.adminAssetsCreate = await btnCount(/新建资产|创建资产/);
  await page.goto(`${BASE}/tasks`);
  await page.waitForTimeout(2000);
  results.adminTasksCreate = await btnCount(/新建任务|创建任务/);
  await page.screenshot({ path: `${OUT}/03-admin-tools.png`, fullPage: true });

  // ===== 中英切换：登录页 → 仪表盘 → 通知，刷新保持 =====
  await logout();
  await page.goto(`${BASE}/login`);
  await page.waitForTimeout(1200);
  await page.locator('[data-testid="lang-switcher"]').click();
  await page.waitForTimeout(500);
  await page.locator('.ant-dropdown-menu-item', { hasText: 'English' }).first().click();
  await page.waitForTimeout(900);
  const loginText = await page.locator('body').innerText();
  results.loginEnglish = /Username|Password|Sign in|Login/i.test(loginText) && !/用户名/.test(loginText);
  results.htmlLang = await page.evaluate(() => document.documentElement.lang);
  await page.reload();
  await page.waitForTimeout(1200);
  results.loginEnglishAfterReload = /Username|Password|Sign in/i.test(await page.locator('body').innerText());
  await page.screenshot({ path: `${OUT}/04-login-en.png`, fullPage: true });

  await doLogin('admin', 'admin');
  const dash = await page.locator('body').innerText();
  results.dashboardEnglish = /Dashboard|Export|Layout|Refresh/i.test(dash);
  await page.screenshot({ path: `${OUT}/05-dashboard-en.png`, fullPage: true });
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1800);
  const notify = await page.locator('body').innerText();
  results.notificationsEnglish = /Notifications|Mark all as read|Announcement/i.test(notify);
  await page.screenshot({ path: `${OUT}/06-notifications-en.png`, fullPage: true });

  // 切回中文
  await page.locator('[data-testid="lang-switcher"]').first().click().catch(() => {});
  await page.waitForTimeout(400);
  await page.locator('.ant-dropdown-menu-item', { hasText: '简体中文' }).first().click().catch(() => {});
  await page.waitForTimeout(800);
  results.backToChinese = /仪表盘|通知|退出登录/.test(await page.locator('body').innerText());
} finally {
  await page.screenshot({ path: `${OUT}/99-last.png`, fullPage: true }).catch(() => {});
  await browser.close();
}

results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t54.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const ok =
  results.engineerAssetsCreate === 0 &&
  results.engineerGroupsCreate === 0 &&
  results.engineerTasksCreate === 0 &&
  results.engineerToolsDelete === 0 &&
  (results.engineerToolsUploadDisabled === true || results.engineerToolsUploadExists === 0) &&
  results.leaderTasksCreate > 0 &&
  results.leaderAssetsCreate > 0 &&
  results.leaderToolsUploadDisabled === false &&
  results.leaderToolsDelete === 0 &&
  results.adminToolsUploadDisabled === false &&
  results.adminToolsDelete > 0 &&
  results.adminAssetsCreate > 0 && results.adminTasksCreate > 0 &&
  results.loginEnglish === true && results.loginEnglishAfterReload === true &&
  results.dashboardEnglish === true && results.notificationsEnglish === true &&
  results.backToChinese === true && errors.length === 0;
console.log(ok ? 'T54_VERIFY: PASS' : 'T54_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
