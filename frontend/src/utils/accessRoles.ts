/**
 * 角色 → 能力判定（纯函数，无运行时依赖，便于单测与复用）。
 *
 * 与后端 @PreAuthorize 口径对齐（t40 起）：
 * - 公告写 POST/PUT/DELETE /notifications/announcements**：ADMIN / PROJECT_LEADER
 * - 环境写 POST/PUT/DELETE /environments**、/deploy|/start|/stop、/snapshots**：ADMIN / PROJECT_LEADER / TEAM_LEADER
 * - 审计日志读 / 报表导出：ADMIN / PROJECT_LEADER
 * 查询接口（团队/环境/公告列表）保持对全部登录角色开放，前端不据此隐藏只读入口。
 */

export interface MyAccessProfile {
  userId?: string;
  username?: string;
  roles: string[];
  permissions: string[];
}

const includesRole = (profile: MyAccessProfile | null, roles: readonly string[]): boolean =>
  Boolean(profile?.roles?.some((role) => roles.includes(role)));

/* ==================== 管理层 ==================== */

export const MANAGEMENT_ROLES = ['admin', 'project_leader'] as const;

export const isManagementProfile = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, MANAGEMENT_ROLES) ||
  Boolean(
    profile?.permissions?.includes('audit:read') || profile?.permissions?.includes('audit:export')
  );

export const canReadAuditLogs = (profile: MyAccessProfile | null): boolean => isManagementProfile(profile);

/* ==================== 公告 ==================== */

export const ANNOUNCEMENT_WRITE_ROLES = ['admin', 'project_leader'] as const;

/** 公告写权限：角色命中优先；权限点作为 RBAC 目录已初始化时的补充 */
export const canManageAnnouncements = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, ANNOUNCEMENT_WRITE_ROLES) ||
  Boolean(profile?.permissions?.includes('notification:announcement:write'));

export const ANNOUNCEMENT_FORBIDDEN_MESSAGE = '当前角色无权管理公告（仅管理员 / 项目负责人可写）';
export const ANNOUNCEMENT_READONLY_HINT = '只读视图：公告的创建 / 编辑 / 删除仅管理员与项目负责人可用';

/* ==================== 环境 ==================== */

export const ENVIRONMENT_WRITE_ROLES = ['admin', 'project_leader', 'team_leader'] as const;

export const canWriteEnvironment = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, ENVIRONMENT_WRITE_ROLES);

export const ENVIRONMENT_FORBIDDEN_MESSAGE =
  '当前角色无权写入环境（仅管理员 / 项目负责人 / 小组长可写）';
export const ENVIRONMENT_READONLY_HINT = '当前角色仅可查看环境与快照';

/* ==================== 报表导出 ==================== */

/** GET /dashboard/export 仅 admin / project_leader（团队长也不可见） */
export const canExportReports = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, MANAGEMENT_ROLES);

/* ==================== 统计口径 ==================== */

export type StatsScopeTone = 'full' | 'scoped' | 'unknown';

export interface StatsScopeInfo {
  tone: StatsScopeTone;
  label: string;
  canViewVulnStats: boolean;
  roleLabel: string;
}

const ROLE_LABELS: Record<string, string> = {
  admin: '系统管理员',
  project_leader: '项目负责人',
  team_leader: '小组长',
  vulnerability_engineer: '漏洞挖掘工程师',
};

export const resolveStatsScope = (profile: MyAccessProfile | null): StatsScopeInfo => {
  const roles = profile?.roles ?? [];
  const roleLabel = roles.length ? roles.map((role) => ROLE_LABELS[role] ?? role).join('、') : '未识别角色';

  if (includesRole(profile, MANAGEMENT_ROLES)) {
    return { tone: 'full', label: '全量', canViewVulnStats: true, roleLabel };
  }
  if (roles.includes('team_leader')) {
    return { tone: 'scoped', label: '当前可见范围（本团队）', canViewVulnStats: true, roleLabel };
  }
  if (roles.length === 0) {
    // 画像未返回（接口失败 / 新账号无角色）→ 未知，允许发起请求，403 再兜底
    return { tone: 'unknown', label: '当前可见范围', canViewVulnStats: true, roleLabel };
  }
  return { tone: 'scoped', label: '当前可见范围', canViewVulnStats: false, roleLabel };
};

export const statsScopeTagColor = (tone: StatsScopeTone): string => (tone === 'full' ? 'blue' : 'default');

/* ==================== 运营管理层（admin / project_leader / team_leader） ==================== */

/**
 * 「运营管理层」= admin / project_leader / team_leader。
 *
 * 与后端 t49~t51 收口后的写侧门槛对齐：
 * - POST /assets、资产分组写、POST /tasks、POST /notifications/vulnerability-discovered、
 *   PUT /tools/{id}、POST /tools/{id}/versions
 * - 知识库删除的管理层分支
 */
export const OPERATIONS_ROLES = ['admin', 'project_leader', 'team_leader'] as const;

/**
 * 解析「当前用户 ID」：优先取权限画像里的 userId（`/users/me/permissions` 必返回），
 * 退回登录态 store 的 id。
 * `/auth/me` 返回的是扁平 `userId` 字段，store 里的 `id` 可能为空——这正是 t52 首轮实测中
 * 「资产负责人自己也被判成不可写」的原因。
 */
export const resolveCurrentUserId = (
  profile: MyAccessProfile | null,
  storeUserId?: string,
): string | undefined => profile?.userId || storeUserId;

export const isOperationsProfile = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, OPERATIONS_ROLES);

/* ---------- 资产 ---------- */

/** POST /assets：admin / project_leader / team_leader */
export const canCreateAsset = (profile: MyAccessProfile | null): boolean => isOperationsProfile(profile);

/** 资产分组写（新建/编辑/删除/加移资产）：admin / project_leader / team_leader */
export const canManageAssetGroups = (profile: MyAccessProfile | null): boolean =>
  isOperationsProfile(profile);

/**
 * 资产写权（前端近似判定，与后端 canWriteAsset 同向）：管理层 / 小组长，或「我就是该资产负责人」。
 * 后端还要求「写 ⊂ 读」（团队长仅本团队资产），前端拿不到该集合，403 仍需兜底提示。
 */
export const canWriteAsset = (
  profile: MyAccessProfile | null,
  assetOwnerId: string | undefined,
  currentUserId: string | undefined,
): boolean =>
  isOperationsProfile(profile) ||
  Boolean(currentUserId && assetOwnerId && assetOwnerId === currentUserId);

/** 资产写入口（新建版本 / 设为默认 / 设为最新 / 删除标签）的置灰说明 */
export const ASSET_WRITE_FORBIDDEN_TIP = '仅资产负责人或管理员/项目负责人/小组长可写该资产';

/* ---------- 知识库 ---------- */

/** DELETE /knowledge/{id}：管理层（admin/PL/TL）或作者本人 */
export const canDeleteKnowledge = (
  profile: MyAccessProfile | null,
  authorId: string | undefined,
  currentUserId: string | undefined,
): boolean =>
  isOperationsProfile(profile) ||
  Boolean(currentUserId && authorId && authorId === currentUserId);

export const KNOWLEDGE_DELETE_FORBIDDEN_TIP = '仅作者本人或管理员/项目负责人/小组长可删除文章';

/* ---------- 工具 ---------- */

/** PUT /tools/{id}、POST /tools/{id}/versions、POST /tools/{id}/file：admin/PL/TL */
export const canManageTools = (profile: MyAccessProfile | null): boolean => isOperationsProfile(profile);

/** DELETE /tools/{id}：仅 admin / project_leader */
export const canDeleteTool = (profile: MyAccessProfile | null): boolean =>
  includesRole(profile, MANAGEMENT_ROLES);

export const TOOL_WRITE_FORBIDDEN_TIP = '仅管理员/项目负责人/小组长可编辑工具与上传版本';
export const TOOL_DELETE_FORBIDDEN_TIP = '仅管理员/项目负责人可删除工具';

/**
 * 工具标签增删（t55 收口）：POST /tools/{id}/tags、DELETE /tools/{id}/tags?tag=xxx
 * 与 POST /tools/{id}/file 同为 `hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')`，即与 `canManageTools` 同权。
 *
 * 分工：前端只收敛「入口可见性」，**后端 @PreAuthorize 才是唯一权威**；
 * 任何越权请求（例如页面角色画像过期、直接调接口）由后端 403 + 下面这条文案兜底。
 */
export const TOOL_TAG_FORBIDDEN_MESSAGE = '无权修改该工具标签（需为管理员 / 项目负责人 / 小组长）';
export const TOOL_TAG_READONLY_HINT = '只读视图：标签的新增 / 移除仅管理员 / 项目负责人 / 小组长可用';
export const TOOL_FILE_FORBIDDEN_MESSAGE = '无权上传工具包（需为管理员 / 项目负责人 / 小组长）';

/* ---------- 知识库 / 文档标签（t61：作者本人或管理层） ---------- */

/**
 * 知识库文章标签（POST /knowledge/{id}/tags、DELETE /knowledge/{id}/tags?tag=xxx）：
 * 后端 `canWriteKnowledgeTags` = 管理层（admin/PL/TL）或文章作者本人。
 *
 * 前端用画像里的 userId 与文章 authorId 比对；画像未返回时 fail-closed（不渲染写控件）。
 */
export const canWriteKnowledgeTags = (
  profile: MyAccessProfile | null,
  authorId: string | undefined,
): boolean =>
  isOperationsProfile(profile) ||
  Boolean(profile?.userId && authorId && profile.userId === authorId);

export const KNOWLEDGE_TAG_FORBIDDEN_MESSAGE = '无权修改该文章标签（需为作者或管理员）';
export const KNOWLEDGE_TAG_READONLY_HINT = '只读视图：仅作者或管理员可修改标签';

/** 文档标签（POST /documents/{id}/tags、DELETE /documents/{id}/tags/{tagId}）：管理层或文档作者本人 */
export const canWriteDocumentTags = (
  profile: MyAccessProfile | null,
  createdBy: string | undefined,
): boolean =>
  isOperationsProfile(profile) ||
  Boolean(profile?.userId && createdBy && profile.userId === createdBy);

export const DOCUMENT_TAG_FORBIDDEN_MESSAGE = '无权修改该文档标签（需为作者或管理员）';
export const DOCUMENT_TAG_READONLY_HINT = '只读视图：仅作者或管理员可修改标签';

/* ---------- 任务 ---------- */

/** POST /tasks：admin/PL/TL（目标资产写权由后端守卫兜底） */
export const canCreateTask = (profile: MyAccessProfile | null): boolean => isOperationsProfile(profile);

/**
 * POST /tasks/{id}/submit-review：任务负责人 / 协作者 / 管理层。
 * 与 t50「管理层可代办提交」口径一致。
 */
export const canSubmitTaskReview = (
  profile: MyAccessProfile | null,
  task: { assigneeId?: string; collaboratorIds?: string[] } | null | undefined,
  currentUserId: string | undefined,
): boolean => {
  if (isOperationsProfile(profile)) return true;
  if (!currentUserId || !task) return false;
  return task.assigneeId === currentUserId || (task.collaboratorIds ?? []).includes(currentUserId);
};

export const TASK_REVIEW_FORBIDDEN_TIP = '仅任务负责人、协作者或管理层可提交审核';

/* ---------- 通知 ---------- */

/** GET /notifications/{id}/recipients：本人（接收人）或管理层（admin/PL） */
export const canReadNotificationRecipients = (
  profile: MyAccessProfile | null,
  isRecipient: boolean,
): boolean => includesRole(profile, MANAGEMENT_ROLES) || isRecipient;

/** POST /notifications/vulnerability-discovered：admin/PL/TL */
export const canSendVulnerabilityNotification = (profile: MyAccessProfile | null): boolean =>
  isOperationsProfile(profile);

/* ---------- 团队 ---------- */

/** GET /teams/{id}/members：非可见团队后端 403，前端展示可读提示而不是空白表 */
export const TEAM_MEMBERS_FORBIDDEN_MESSAGE = '无权查看该团队成员（仅可查看自己领导或所属团队）';
