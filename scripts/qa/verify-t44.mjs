// t44 最终发布页面级验证：团队/公告/环境快照的角色可见性（线上 8089 新 dist）
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://192.168.188.200:8089';
const API = `${BASE}/api/v1`;
const OUT = 'D:/WorkDirQiax/Vuln-Audit-Lifecycle-Management/.tmp-t44';
fs.mkdirSync(OUT, { recursive: true });
const PASS = 'Smoke@12345';
const SUFFIX = String(Date.now()).slice(-6);
const TEAM_NAME = `t44-验证团队-${SUFFIX}`;
const ENV_NAME = `t44-环境-${SUFFIX}`;

const log = (...a) => console.log('[t44]', ...a);
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
const leader = await login('smoke_leader', PASS);
const engineer = await login('smoke_engineer', PASS);
const adminToken = admin.token;
log(`账号就绪: leader=${leader.userId} engineer=${engineer.userId}`);

// 清理历史残留（t44-验证团队-*）
const stale = (await api('/teams/all', { token: adminToken })).json.data.filter((t) => (t.name || '').startsWith('t44-验证团队-'));
for (const t of stale) await api(`/teams/${t.id}`, { method: 'DELETE', token: adminToken });

// 临时团队（leader=smoke_leader）
const created = await api('/teams', {
  method: 'POST', token: adminToken,
  body: { name: TEAM_NAME, description: 't44 可见性验证，用完删除', leaderId: leader.userId, createdBy: admin.userId },
});
if (![200, 201].includes(created.status)) throw new Error(`建团队失败: ${JSON.stringify(created.json)}`);
const teamId = created.json.data.id;

// 选一个资产并建临时环境（快照面板需要环境行）
// 工程师可见的资产（列表接口按可见范围过滤），保证 /assets/{id} 不被数据级守卫拦成 404
const engAssets = (await api('/assets?page=0&size=20', { token: engineer.token })).json.data;
const engList = engAssets.content ?? engAssets.items ?? engAssets.records ?? [];
const assetId = engList[0]?.id;
if (!assetId) throw new Error('工程师没有可见资产，无法验证环境快照');
const envCreated = await api('/environments', {
  method: 'POST', token: adminToken,
  body: { name: ENV_NAME, assetId, templateId: '00000000-0000-0000-0000-000000000001', config: '{}' },
});
const envId = envCreated.json?.data?.id ?? null;
log(`team=${teamId} asset=${assetId} env=${envId} (env status ${envCreated.status})`);

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message.slice(0, 200)}`));
page.on('request', (r) => { if (r.url().includes('/api/')) reqs.push(`${r.method()} ${r.url().replace(BASE, '')}`); });

const doLogin = async (username, password) => {
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL('**/dashboard', { timeout: 30000 });
};
const logout = async () => { await page.evaluate(() => localStorage.clear()); await context.clearCookies(); };
const btn = async (name) => page.getByRole('button', { name }).count();
const gotoTeams = async () => {
  await page.goto(`${BASE}/teams`);
  await page.locator('.ant-tree-node-content-wrapper').first().waitFor({ timeout: 30000 });
  await page.waitForTimeout(1200);
};
const clickTeam = async (name) => {
  await page.locator('.ant-tree-node-content-wrapper', { hasText: name }).first().click();
  await page.waitForTimeout(900);
};

try {
  // ===== 1) admin：团队写入口全可见 =====
  await doLogin('admin', 'admin');
  await gotoTeams();
  results.adminCreateVisible = (await btn(/创建团队/)) > 0;
  await clickTeam(TEAM_NAME);
  results.adminChildVisible = (await btn(/新增子团队/)) > 0;
  results.adminEditVisible = (await btn(/^编\s*辑$/)) > 0;
  results.adminDeleteVisible = (await btn(/^删\s*除$/)) > 0;
  results.adminAddMemberVisible = (await btn(/添加成员/)) > 0;
  await page.screenshot({ path: `${OUT}/01-admin-teams.png`, fullPage: true });

  // ===== 2) 公告区：admin 可见写入口 =====
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1500);
  await page.getByRole('tab', { name: /公\s*告/ }).click().catch(() => {});
  await page.waitForTimeout(1200);
  results.adminAnnounceWriteVisible = (await btn(/发布公告/)) > 0;
  results.adminAnnounceListRendered = (await page.getByText('生效中的公告').count()) > 0;
  await page.screenshot({ path: `${OUT}/02-admin-announcements.png`, fullPage: true });

  // ===== 3) admin：快照缺 name 前端拦截（不发请求） =====
  await page.goto(`${BASE}/assets/${assetId}`);
  await page.waitForTimeout(1800);
  await page.getByRole('tab', { name: /环\s*境/ }).click().catch(() => {});
  await page.waitForTimeout(1200);
  results.adminSnapshotEntryVisible = (await page.locator('a', { hasText: '快照' }).count()) > 0;
  if (results.adminSnapshotEntryVisible) {
    await page.locator('a', { hasText: '快照' }).first().click();
    await page.waitForTimeout(1000);
    const nameInput = page.getByPlaceholder('快照名称（必填，缺名会返回 400）');
    await nameInput.fill('');
    const before = reqs.filter((r) => r.includes('/snapshots') && r.startsWith('POST')).length;
    await page.getByRole('button', { name: '创建快照' }).click();
    await page.waitForTimeout(1200);
    const after = reqs.filter((r) => r.includes('/snapshots') && r.startsWith('POST')).length;
    results.snapshotEmptyNameBlockedLocally = after === before;
    results.snapshotWarningShown = (await page.getByText(/请先填写快照名称/).count()) > 0;
    await page.screenshot({ path: `${OUT}/03-admin-snapshot-empty.png`, fullPage: true });
  }

  // ===== 4) 团队长：仅自己团队可编辑/管成员 =====
  await logout();
  await doLogin('smoke_leader', PASS);
  await gotoTeams();
  results.leaderTreeRendered = (await page.locator('.ant-tree-node-content-wrapper').count()) >= 2;
  results.leaderCreateHidden = (await btn(/创建团队/)) === 0;
  await clickTeam(TEAM_NAME);
  results.leaderOwnEditVisible = (await btn(/^编\s*辑$/)) > 0;
  results.leaderOwnDeleteHidden = (await btn(/^删\s*除$/)) === 0;
  results.leaderOwnChildHidden = (await btn(/新增子团队/)) === 0;
  results.leaderOwnAddMemberVisible = (await btn(/添加成员/)) > 0;
  const others = (await api('/teams/all', { token: adminToken })).json.data.filter(
    (t) => t.id !== teamId && (t.leaderId ?? null) !== leader.userId && !(t.name || '').startsWith('t44-'),
  );
  if (others[0]) {
    await clickTeam(others[0].name);
    results.leaderOtherEditHidden = (await btn(/^编\s*辑$/)) === 0;
    results.leaderOtherAddMemberHidden = (await btn(/添加成员/)) === 0;
    results.leaderOtherReadonlyHint = (await page.getByText('仅可查看成员（无管理权限）').count()) > 0;
  }
  await page.screenshot({ path: `${OUT}/04-leader-teams.png`, fullPage: true });

  // 公告区：团队长无写入口
  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1500);
  await page.getByRole('tab', { name: /公\s*告/ }).click().catch(() => {});
  await page.waitForTimeout(1200);
  results.leaderAnnounceWriteHidden = (await btn(/发布公告/)) === 0;
  results.leaderAnnounceReadonlyHint = (await page.getByText(/只读视图：公告的创建/).count()) > 0;

  // ===== 5) 工程师：团队/公告全无写入口；环境快照只读 =====
  await logout();
  await doLogin('smoke_engineer', PASS);
  await gotoTeams();
  results.engineerTreeRendered = (await page.locator('.ant-tree-node-content-wrapper').count()) >= 2;
  results.engineerCreateHidden = (await btn(/创建团队/)) === 0;
  await clickTeam(TEAM_NAME);
  results.engineerEditHidden = (await btn(/^编\s*辑$/)) === 0;
  results.engineerDeleteHidden = (await btn(/^删\s*除$/)) === 0;
  results.engineerChildHidden = (await btn(/新增子团队/)) === 0;
  results.engineerAddMemberHidden = (await btn(/添加成员/)) === 0;
  results.engineerRemoveLinkHidden = (await page.getByText('移除', { exact: true }).count()) === 0;
  results.engineerReadonlyHint = (await page.getByText('只读视角').count()) > 0;
  results.engineerLeaderHint = (await page.getByText('团队长视角').count()) > 0;
  // 数据级语义：若工程师被设为某团队 leaderId，该团队会出现管理入口（与后端 canManageTeam 一致）
  results.engineerIsTeamLeaderInData = (await api('/teams/all', { token: adminToken })).json.data.some(
    (t) => (t.leaderId ?? '') === engineer.userId,
  );
  results.engineerMemberTableVisible = (await page.locator('th', { hasText: '成员' }).count()) > 0;
  await page.screenshot({ path: `${OUT}/05-engineer-teams.png`, fullPage: true });

  await page.goto(`${BASE}/notifications`);
  await page.waitForTimeout(1500);
  await page.getByRole('tab', { name: /公\s*告/ }).click().catch(() => {});
  await page.waitForTimeout(1200);
  results.engineerAnnounceWriteHidden = (await btn(/发布公告/)) === 0;
  results.engineerAnnounceReadonlyHint = (await page.getByText(/只读视图：公告的创建/).count()) > 0;
  results.engineerAnnounceListRendered = (await page.getByText('生效中的公告').count()) > 0;
  await page.screenshot({ path: `${OUT}/06-engineer-announcements.png`, fullPage: true });

  await page.goto(`${BASE}/assets/${assetId}`);
  await page.waitForTimeout(1800);
  await page.getByRole('tab', { name: /环\s*境/ }).click().catch(() => {});
  await page.waitForTimeout(1200);
  const addEnv = page.getByRole('button', { name: /添加环境/ }).first();
  results.engineerAddEnvDisabled = (await addEnv.count()) > 0 ? await addEnv.isDisabled() : null;
  const snapBtnCount = await btn(/快\s*照/);
  if (snapBtnCount > 0) {
    await page.locator('a', { hasText: '快照' }).first().click();
    await page.waitForTimeout(1000);
    results.engineerSnapshotCreateHidden = (await btn(/创建快照/)) === 0;
  } else {
    results.engineerSnapshotHiddenEntirely = true;
  }
  await page.screenshot({ path: `${OUT}/07-engineer-asset-env.png`, fullPage: true });
} finally {
  await page.screenshot({ path: `${OUT}/99-last.png`, fullPage: true }).catch(() => {});
  await browser.close();
  if (envId) results.cleanupEnv = (await api(`/environments/${envId}`, { method: 'DELETE', token: adminToken })).status;
  const members = (await api(`/teams/${teamId}/members`, { token: adminToken })).json?.data ?? [];
  for (const userId of members) await api(`/teams/${teamId}/members/${userId}`, { method: 'DELETE', token: adminToken });
  results.cleanupTeam = (await api(`/teams/${teamId}`, { method: 'DELETE', token: adminToken })).status;
}

results.pageErrors = errors;
results.snapshotPostRequests = reqs.filter((r) => r.includes('/snapshots') && r.startsWith('POST'));
log(JSON.stringify(results, null, 2));
fs.writeFileSync(`${OUT}/verify-t44.json`, JSON.stringify(results, null, 2));

const ok =
  results.adminCreateVisible && results.adminChildVisible && results.adminEditVisible &&
  results.adminDeleteVisible && results.adminAddMemberVisible &&
  results.adminAnnounceWriteVisible && results.adminAnnounceListRendered &&
  (results.adminSnapshotEntryVisible !== true || (results.snapshotEmptyNameBlockedLocally && results.snapshotWarningShown)) &&
  results.leaderTreeRendered && results.leaderCreateHidden && results.leaderOwnEditVisible &&
  results.leaderOwnDeleteHidden && results.leaderOwnChildHidden && results.leaderOwnAddMemberVisible &&
  (results.leaderOtherEditHidden === undefined || (results.leaderOtherEditHidden && results.leaderOtherAddMemberHidden && results.leaderOtherReadonlyHint)) &&
  results.leaderAnnounceWriteHidden && results.leaderAnnounceReadonlyHint &&
  results.engineerTreeRendered && results.engineerCreateHidden && results.engineerEditHidden &&
  results.engineerDeleteHidden && results.engineerChildHidden && results.engineerAddMemberHidden &&
  results.engineerRemoveLinkHidden && (results.engineerReadonlyHint || results.engineerLeaderHint) && results.engineerMemberTableVisible &&
  results.engineerAnnounceWriteHidden && results.engineerAnnounceReadonlyHint && results.engineerAnnounceListRendered &&
  results.engineerAddEnvDisabled === true &&
  (results.engineerSnapshotCreateHidden === true || results.engineerSnapshotHiddenEntirely === true) &&
  errors.length === 0;
log(ok ? 'T44_VERIFY: PASS' : 'T44_VERIFY: FAIL');
process.exit(ok ? 0 : 1);
