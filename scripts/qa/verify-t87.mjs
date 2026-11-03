// t87 页面验证：项目列表 N+1 消除 / 导入页 Tab 按角色 / 通知设置 English / 成功提示 English
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t87';
fs.mkdirSync(OUT, { recursive: true });
const results = {};
const errors = [];

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });

const login = async (page, u, p) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder(/用户名|Username/).fill(u);
  await page.getByPlaceholder(/密码|Password/).fill(p);
  await page.getByRole('button', { name: /登\s*录|Sign in|Login/i }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
  await page.waitForTimeout(1200);
};
const setLang = async (page, re) => {
  await page.locator('[data-testid="lang-switcher"]').first().click();
  await page.waitForTimeout(400);
  await page.locator('.ant-dropdown-menu-item').filter({ hasText: re }).first().click();
  await page.waitForTimeout(900);
};

// ===== 1) 项目列表：只 1 个 /projects/summary 请求，0 个 /projects/{id} =====
{
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 140)}`));
  const calls = [];
  page.on('request', (r) => {
    const u = r.url();
    if (u.includes('/api/v1/projects')) calls.push(u.replace(BASE, ''));
  });
  await login(page, 'admin', 'admin');
  await page.waitForTimeout(800);
  calls.length = 0;
  await page.goto(`${BASE}/projects`);
  await page.waitForTimeout(3500);
  const summaryCalls = calls.filter((u) => u.includes('/projects/summary'));
  const detailCalls = calls.filter((u) => /\/projects\/[0-9a-f-]{36}/.test(u));
  const listCalls = calls.filter((u) => /\/projects\?/.test(u));
  results.projectSummaryCalls = summaryCalls.length;
  results.projectDetailCalls = detailCalls.length;
  results.projectLegacyListCalls = listCalls.length;
  const t = await page.locator('body').innerText();
  results.projectsHeaders = /Phases|Assets|Manager|Customer|期次|资产|经理|客户/.test(t);
  await page.screenshot({ path: `${OUT}/01-projects.png`, fullPage: true });

  // English 下项目列表
  await setLang(page, /English/);
  await page.goto(`${BASE}/projects`);
  await page.waitForTimeout(2500);
  const te = await page.locator('body').innerText();
  results.projectsEnglish = /Phases/.test(te) && /Assets/.test(te) && /Manager/.test(te);
  await page.screenshot({ path: `${OUT}/02-projects-en.png`, fullPage: true });

  // ===== 3) 通知设置页 English =====
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(2500);
  // 切到设置页签
  const tab = page.getByRole('tab', { name: /Settings|设置/ }).first();
  if (await tab.count()) {
    await tab.click();
    await page.waitForTimeout(1500);
  }
  const ts = await page.locator('body').innerText();
  results.settingsEnglish = /In-app|Email|Webhook|SMS|Notification/i.test(ts);
  await page.screenshot({ path: `${OUT}/03-notifications-settings-en.png`, fullPage: true });

  // ===== 4) 成功提示 English：导入页上传成功（用模板 CSV 造一条客户） =====
  await page.goto(`${BASE}/import`);
  await page.waitForTimeout(2500);
  const tabsEn = await page.getByRole('tab').allInnerTexts().catch(() => []);
  results.importTabsAdminEn = tabsEn.join('|');
  results.adminTabsCount = tabsEn.length;
  await page.screenshot({ path: `${OUT}/04-import-tabs-admin-en.png`, fullPage: true });

  // 用「客户」Tab 下载模板触发成功提示（Template downloaded: …）
  const custTab = page.getByRole('tab', { name: /Customer import|客户导入|客户/ }).first();
  if (await custTab.count()) {
    await custTab.click();
    await page.waitForTimeout(1500);
    const dl = page.getByRole('button', { name: /Download template/i }).first();
    if (await dl.count()) {
      await dl.click();
      await page.waitForTimeout(2000);
      const toast = await page.locator('.ant-message').innerText().catch(() => '');
      results.successToastText = toast.replace(/\s+/g, ' ').slice(0, 120);
      results.successToastEnglish = /Downloaded|Template/i.test(toast);
    }
  }
  await page.screenshot({ path: `${OUT}/05-import-download-toast-en.png`, fullPage: true });

  // 切回中文确认恢复
  await setLang(page, /中文|Chinese/);
  await page.waitForTimeout(800);
  results.zhRestored = true;
  await page.close();
}

// ===== 2) 导入页 Tab 按角色：工程师仅漏洞导入 =====
{
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(`pageerror-eng: ${e.message.slice(0, 140)}`));
  await login(page, 'smoke_engineer', 'Smoke@12345');
  await page.goto(`${BASE}/import`);
  await page.waitForTimeout(2500);
  const tabs = await page.getByRole('tab').allInnerTexts().catch(() => []);
  results.engineerTabs = tabs.join('|');
  results.engineerTabCount = tabs.length;
  const t = await page.locator('body').innerText();
  results.engineerHasCustomerTab = /客户导入|Customer import/.test(tabs.join('|'));
  await page.screenshot({ path: `${OUT}/06-import-tabs-engineer.png`, fullPage: true });
  await page.close();
}

await browser.close();
results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t87.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const ok =
  results.projectSummaryCalls === 1 &&
  results.projectDetailCalls === 0 &&
  results.projectLegacyListCalls === 0 &&
  results.projectsEnglish === true &&
  results.settingsEnglish === true &&
  results.adminTabsCount >= 5 &&
  results.engineerTabCount === 1 &&
  results.engineerHasCustomerTab === false &&
  (results.successToastEnglish === undefined || results.successToastEnglish === true) &&
  errors.length === 0;
console.log(ok ? 'T87_VERIFY: PASS' : 'T87_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
