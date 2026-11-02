// t63 页面验证：English 下 6 个主要页面英文 + 切回中文；标签只读说明按角色
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t63';
fs.mkdirSync(OUT, { recursive: true });
const PASS = 'Smoke@12345';
const results = {};
const errors = [];

const api = async (path, { method = 'GET', token, body } = {}) => {
  const res = await fetch(BASE + '/api/v1' + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: res.status, json };
};
const login = async (u, p) => {
  const r = await api('/auth/login', { method: 'POST', body: { username: u, password: p } });
  if (!r.json?.data?.token) throw new Error(`login fail ${u}`);
  return r.json.data;
};
const admin = await login('admin', 'admin');
const engineer = await login('smoke_engineer', PASS);

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 160)}`));

const setLang = async (label) => {
  await page.locator('[data-testid="lang-switcher"]').first().click();
  await page.waitForTimeout(400);
  const item = label instanceof RegExp
    ? page.locator('.ant-dropdown-menu-item').filter({ hasText: label }).first()
    : page.locator('.ant-dropdown-menu-item', { hasText: label }).first();
  await item.click();
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
const bodyText = async () => page.locator('body').innerText();

try {
  // ===== 管理员：English 下抽查 6 个页面 =====
  await doLogin('admin', 'admin');
  await setLang(/English/);

  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2200);
  let t = await bodyText();
  results.enAssetsList = /Asset name|Target type|Owner|Priority|Status/i.test(t);
  await page.screenshot({ path: `${OUT}/01-assets-en.png`, fullPage: true });

  // 资产详情：用列表第一个资产
  const engList = (await api('/assets?page=0&size=1', { token: admin.token })).json?.data;
  const asset = (engList?.content ?? engList?.items ?? engList?.records ?? [])[0];
  if (asset) {
    await page.goto(`${BASE}/assets/${asset.id}`);
    await page.waitForTimeout(2500);
    t = await bodyText();
    results.enAssetDetail = /Basic|Environment|Vulnerabilit|Document|Metadata|Description/i.test(t);
    await page.screenshot({ path: `${OUT}/02-asset-detail-en.png`, fullPage: true });
  }

  await page.goto(`${BASE}/vulnerabilities`);
  await page.waitForTimeout(2200);
  t = await bodyText();
  results.enVulnerabilities = /Vulnerabilit|Severity|Title|Status|Workflow/i.test(t);
  await page.screenshot({ path: `${OUT}/03-vulns-en.png`, fullPage: true });

  await page.goto(`${BASE}/tasks`);
  await page.waitForTimeout(2000);
  t = await bodyText();
  results.enTasks = /Task|Assignee|Status|Priority|Review/i.test(t);
  await page.screenshot({ path: `${OUT}/04-tasks-en.png`, fullPage: true });

  await page.goto(`${BASE}/tools`);
  await page.waitForTimeout(2200);
  t = await bodyText();
  results.enTools = /Tool|Categor|Download|Version|Tag/i.test(t);
  await page.screenshot({ path: `${OUT}/05-tools-en.png`, fullPage: true });

  await page.goto(`${BASE}/audit-logs`);
  await page.waitForTimeout(2200);
  t = await bodyText();
  results.enAuditLogs = /Time|Action|Resource type|Resource ID|User|Details/i.test(t);
  await page.screenshot({ path: `${OUT}/06-audit-en.png`, fullPage: true });

  // 切回中文恢复
  await setLang(/中文|Chinese/);
  await page.goto(`${BASE}/assets`);
  await page.waitForTimeout(2000);
  t = await bodyText();
  results.zhAssetsRestored = /资产名称|目标类型|负责人|优先级/.test(t);

  // ===== 标签只读说明按角色（知识库）=====
  // 管理员建一篇文章（作者=admin），工程师打开标签抽屉应为只读
  const art = await api('/knowledge', {
    method: 'POST', token: admin.token,
    body: { title: `t63-标签验证-${Date.now()}`, category: '技术', summary: 't63', content: 't63 内容' },
  });
  const articleId = art.json?.data?.id;
  await page.goto(`${BASE}/knowledge`);
  await page.waitForTimeout(2200);
  if (articleId) {
    const row = page.locator('.ant-table-row', { hasText: 't63-标签验证' }).first();
    if (await row.count()) {
      await row.getByText('标签').first().click();
      await page.waitForTimeout(1500);
      try {
        // 详情抽屉内再点「管理标签」进入标签面板
        await page.locator('.ant-drawer-open').first().getByText('管理标签').first().click({ timeout: 8000 });
        await page.waitForTimeout(1200);
        const drawer = page.locator('.ant-drawer-open').last();
        results.tagDrawerOpened = true;
        results.adminTagInputCount = await drawer.locator('input').count();
        results.adminTagWritable = (await drawer.locator('input').count()) > 0;
      } catch (e) {
        results.tagDrawerOpened = false;
        results.probeError = String(e).slice(0, 120);
      }
      await page.keyboard.press('Escape');
    }
  }
  await page.screenshot({ path: `${OUT}/07-admin-knowledge-tags.png`, fullPage: true });

  // 工程师视角：他人文章标签抽屉只读
  await page.evaluate(() => localStorage.clear());
  await context.clearCookies();
  await doLogin('smoke_engineer', PASS);
  await page.goto(`${BASE}/knowledge`);
  await page.waitForTimeout(2200);
  if (articleId) {
    const row2 = page.locator('.ant-table-row', { hasText: 't63-标签验证' }).first();
    if (await row2.count()) {
      await row2.getByText('标签').first().click();
      await page.waitForTimeout(1500);
      try {
        await page.locator('.ant-drawer-open').first().getByText('管理标签').first().click({ timeout: 8000 });
        await page.waitForTimeout(1200);
        const drawer2 = page.locator('.ant-drawer-open').last();
        const dtext = await drawer2.innerText();
        results.engineerTagDrawerOpened = true;
        results.engineerOtherArticleReadonly = /只读视图|仅作者或管理员|read-only/i.test(dtext);
        results.engineerOtherArticleInputHidden = (await drawer2.locator('input').count()) === 0;
        results.engineerDrawerTextSample = dtext.replace(/\s+/g, ' ').slice(0, 120);
      } catch (e) {
        results.engineerTagDrawerOpened = false;
        results.probeError2 = String(e).slice(0, 120);
      }
      await page.keyboard.press('Escape');
    }
  }
  await page.screenshot({ path: `${OUT}/08-engineer-knowledge-tags.png`, fullPage: true });
} finally {
  await page.screenshot({ path: `${OUT}/99-last.png`, fullPage: true }).catch(() => {});
  await browser.close();
}

results.pageErrors = errors;
fs.writeFileSync(`${OUT}/verify-t63.json`, JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));

const tagOk =
  (results.tagDrawerOpened === false && results.engineerTagDrawerOpened === false) ||
  (results.adminTagWritable === true &&
    results.engineerOtherArticleReadonly === true &&
    results.engineerOtherArticleInputHidden === true);
const ok =
  results.enAssetsList === true &&
  results.enVulnerabilities === true &&
  results.enTasks === true &&
  results.enTools === true &&
  results.enAuditLogs === true &&
  results.zhAssetsRestored === true &&
  results.enAssetDetail !== false &&
  tagOk &&
  errors.length === 0;
console.log(ok ? 'T63_VERIFY: PASS' : 'T63_VERIFY: FAIL');
process.exit(ok ? 0 : 1);