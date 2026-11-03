import request from '@/utils/request';
import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 批量导入（资产 / 漏洞 / 用户）。
 *
 * 后端：backend/.../controller/ImportController.java
 * - POST /api/v1/import/assets            multipart，字段名固定为 file
 * - POST /api/v1/import/vulnerabilities   multipart，字段名固定为 file
 * - POST /api/v1/import/users             multipart，字段名固定为 file（仅 ADMIN）
 * - GET  /api/v1/import/templates/{type}  text/csv 附件（带 UTF-8 BOM，Excel 中文不乱码）
 *
 * 语义（务必区分）：
 * - **400**：文件级校验失败（空文件 / 缺表头列 / 超过 20MB），整单不入库；
 * - **200 + failedRows 非空**：部分成功，成功的行已经落库，失败行给出 line + reason。
 */

/** 导入类型（与后端 /import/{type} 路径段、/templates/{type} 保持一值） */
export type ImportType = 'assets' | 'vulnerabilities' | 'users' | 'customers' | 'projects';

/** 导入失败行：line 为 1 基行号（表头是第 1 行） */
export interface ImportFailedRow {
  line: number;
  reason: string;
}

/**
 * 用户导入成功创建的行。
 *
 * `initialPassword` 由后端生成（16 位强随机），**只在本次响应返回一次**：
 * 页面仅保存在组件内存里，不做任何持久化，刷新/离开即丢失。
 */
export interface ImportedUser {
  line: number;
  userId: string;
  username: string;
  roleCode: string;
  initialPassword: string;
  mustChangePassword: boolean;
}

/** 导入结果 */
/** 成功行上的提示（t78）：该行已入库，但挂靠方式等需要提醒 */
export interface ImportNote {
  line: number;
  message: string;
}

export interface ImportResult {
  /** 文件中的数据行数（不含表头与空行） */
  totalRows: number;
  /** 成功入库行数 */
  successRows: number;
  /** 失败行明细 */
  failedRows: ImportFailedRow[];
  /**
   * 成功行提示（t78，仅资产导入「未指定项目」时产生）。
   * 旧后端没有该字段 → 统一归一为空数组，调用方无需判空。
   */
  notes: ImportNote[];
  /** 仅用户导入返回：本次创建的用户与初始密码 */
  createdUsers?: ImportedUser[];
}

/** 单文件上限：与后端 ImportController.MAX_FILE_SIZE 一致（20MB） */
export const IMPORT_MAX_FILE_SIZE = 20 * 1024 * 1024;

/**
 * 上传 CSV 执行导入。
 *
 * - 字段名必须是 `file`（后端 @RequestParam("file")）；
 * - 关闭全局错误提示（silent），由页面区分 400 与 200+failedRows 两种语义；
 * - 单次导入可能耗时较久，单独放宽超时到 120s。
 */
export const importCsv = (type: ImportType, file: File): Promise<any> => {
  const formData = new FormData();
  formData.append('file', file, file.name);
  return request.post(`/import/${type}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    silent: true,
  });
};

/** 下载导入模板（blob）：返回 CSV 字节流，文件名由页面决定 */
export const downloadImportTemplate = (type: ImportType): Promise<Blob> => {
  return request.get(`/import/templates/${type}`, {
    responseType: 'blob',
    silent: true,
  });
};

/** 把导入响应解包成 { result, message }（兼容 ApiResponse 与裸对象） */
export const extractImportResult = (res: unknown): { result: ImportResult; message?: string } => {
  const envelope = res as { data?: unknown; message?: unknown } | undefined;
  const payload = (envelope?.data ?? res ?? {}) as Record<string, unknown>;
  const rawFailed = Array.isArray(payload.failedRows) ? (payload.failedRows as unknown[]) : [];
  const failedRows: ImportFailedRow[] = rawFailed.map((item) => {
    const row = (item ?? {}) as Record<string, unknown>;
    return {
      line: Number(row.line ?? 0),
      reason: typeof row.reason === 'string' ? row.reason : String(row.reason ?? ''),
    };
  });

  // t78：成功行提示（旧后端无该字段 → 空数组）
  const rawNotes = Array.isArray(payload.notes) ? (payload.notes as unknown[]) : [];
  const notes: ImportNote[] = rawNotes.map((item) => {
    const row = (item ?? {}) as Record<string, unknown>;
    return {
      line: Number(row.line ?? 0),
      message: typeof row.message === 'string' ? row.message : String(row.message ?? ''),
    };
  });

  const rawCreated = Array.isArray(payload.createdUsers) ? (payload.createdUsers as unknown[]) : [];
  const createdUsers: ImportedUser[] = rawCreated.map((item) => {
    const row = (item ?? {}) as Record<string, unknown>;
    return {
      line: Number(row.line ?? 0),
      userId: String(row.userId ?? ''),
      username: String(row.username ?? ''),
      roleCode: String(row.roleCode ?? ''),
      initialPassword: String(row.initialPassword ?? ''),
      mustChangePassword: Boolean(row.mustChangePassword),
    };
  });

  return {
    result: {
      totalRows: Number(payload.totalRows ?? 0),
      successRows: Number(payload.successRows ?? 0),
      failedRows,
      notes,
      createdUsers,
    },
    message: typeof envelope?.message === 'string' ? envelope.message : undefined,
  };
};

/** 前端先做一次扩展名与体积校验；返回错误文案，通过则返回 null */
export const validateCsvFile = (file: File): string | null => {
  if (!/\.csv$/i.test(file.name)) {
    return `仅支持 .csv 文件（当前：${file.name}）`;
  }
  if (file.size === 0) {
    return '文件内容为空，请检查后重新选择';
  }
  if (file.size > IMPORT_MAX_FILE_SIZE) {
    return `文件大小 ${(file.size / 1024 / 1024).toFixed(2)}MB 超过 20MB 上限`;
  }
  return null;
};

export interface ImportTypeMeta {
  /** i18n 词条：类型名 / 模板列 / 填写须知 / 目标列表名 */
  labelKey: MessageKey;
  columnsKey: MessageKey;
  tipsKey: MessageKey;
  listLabelKey: MessageKey;
  templateFile: string;
  /** 导入成功后可在该路由核对数据（列表页进入即重新拉取） */
  listPath: string;
  /** 写侧角色门槛（与后端 @PreAuthorize 一致） */
  roleGate: 'public' | 'adminOrProjectLeader' | 'admin';
}

/** 各类型的表头与列说明（与后端模板/文档保持一致） */
export const IMPORT_TYPE_META: Record<ImportType, ImportTypeMeta> = {
  assets: {
    labelKey: 'import.type.assets',
    columnsKey: 'import.columns.assets',
    tipsKey: 'import.tips.assets',
    templateFile: 'assets_template.csv',
    listPath: '/assets',
    listLabelKey: 'import.list.assets',
    // POST /import/assets：ADMIN / PROJECT_LEADER / TEAM_LEADER
    roleGate: 'adminOrProjectLeader',
  },
  vulnerabilities: {
    labelKey: 'import.type.vulnerabilities',
    columnsKey: 'import.columns.vulnerabilities',
    tipsKey: 'import.tips.vulnerabilities',
    templateFile: 'vulnerabilities_template.csv',
    listPath: '/vulnerabilities',
    listLabelKey: 'import.list.vulnerabilities',
    // 不加角色门槛，后端逐行按目标资产写权判定
    roleGate: 'public',
  },
  users: {
    labelKey: 'import.type.users',
    columnsKey: 'import.columns.users',
    tipsKey: 'import.tips.users',
    templateFile: 'users_template.csv',
    listPath: '/users',
    listLabelKey: 'import.list.users',
    roleGate: 'admin',
  },
  customers: {
    labelKey: 'import.type.customers',
    columnsKey: 'import.columns.customers',
    tipsKey: 'import.tips.customers',
    templateFile: 'customers_template.csv',
    listPath: '/customers',
    listLabelKey: 'import.list.customers',
    // POST /import/customers：ADMIN / PROJECT_LEADER（团队长 403）
    roleGate: 'adminOrProjectLeader',
  },
  projects: {
    labelKey: 'import.type.projects',
    columnsKey: 'import.columns.projects',
    tipsKey: 'import.tips.projects',
    templateFile: 'projects_template.csv',
    listPath: '/projects',
    listLabelKey: 'import.list.projects',
    // POST /import/projects：ADMIN / PROJECT_LEADER（团队长 403）
    roleGate: 'adminOrProjectLeader',
  },
};

// ==================== 权限判定与失败分类（t33 / t38） ====================

/** 后端权限拒绝的失败原因前缀（ImportServiceImpl.PERMISSION_DENIED_PREFIX） */
export const PERMISSION_DENIED_PREFIX = '权限拒绝：';

/** 该失败行是否为「写权限被拒」（而非数据校验失败） */
export const isPermissionDeniedFailure = (row: ImportFailedRow): boolean =>
  (row.reason ?? '').trimStart().startsWith(PERMISSION_DENIED_PREFIX);

export interface ImportFailureSplit {
  /** 权限拒绝行：不会写入库 */
  permissionDenied: ImportFailedRow[];
  /** 数据错误行：格式/枚举/必填等校验失败 */
  dataErrors: ImportFailedRow[];
}

/** 把 failedRows 拆成「权限拒绝」与「数据错误」两组（保持原始顺序） */
export const splitImportFailures = (rows: ImportFailedRow[]): ImportFailureSplit => {
  const permissionDenied: ImportFailedRow[] = [];
  const dataErrors: ImportFailedRow[] = [];
  rows.forEach((row) => {
    (isPermissionDeniedFailure(row) ? permissionDenied : dataErrors).push(row);
  });
  return { permissionDenied, dataErrors };
};

/**
 * 角色口径（与后端 @PreAuthorize 对齐）：
 * - 资产导入：admin / project_leader / team_leader（t33 起工程师 403 → 前端不渲染该 Tab）
 * - 用户导入：仅 admin
 * - 漏洞导入：不加角色门槛，逐行按目标资产 canWriteAsset 判定
 */
export const IMPORT_ASSET_ROLES = ['admin', 'project_leader', 'team_leader'] as const;
export const IMPORT_USERS_ROLES = ['admin'] as const;

export const canImportAssets = (roles: string[]): boolean =>
  roles.some((role) => (IMPORT_ASSET_ROLES as readonly string[]).includes(role));

/** 客户/项目导入的写侧角色（后端 @PreAuthorize：ADMIN / PROJECT_LEADER） */
export const IMPORT_CUSTOMER_PROJECT_ROLES = ['admin', 'project_leader'] as const;

/** 客户导入：ADMIN / PROJECT_LEADER（后端 @PreAuthorize，团队长 403） */
export const canImportCustomers = (roles: string[]): boolean =>
  roles.some((role) => (IMPORT_CUSTOMER_PROJECT_ROLES as readonly string[]).includes(role));

/** 项目导入：ADMIN / PROJECT_LEADER */
export const canImportProjects = (roles: string[]): boolean =>
  roles.some((role) => (IMPORT_CUSTOMER_PROJECT_ROLES as readonly string[]).includes(role));

/** 按 meta.roleGate 统一判定某导入类型是否对当前角色开放 */
export const canImportType = (roles: string[], type: ImportType): boolean => {
  switch (IMPORT_TYPE_META[type].roleGate) {
    case 'admin':
      return canImportUsers(roles);
    case 'adminOrProjectLeader':
      return type === 'assets' ? canImportAssets(roles) : canImportCustomers(roles);
    default:
      return true;
  }
};

export const canImportUsers = (roles: string[]): boolean =>
  roles.some((role) => (IMPORT_USERS_ROLES as readonly string[]).includes(role));

/**
 * 是否「团队长视角」：具备 team_leader 且不同时具备 admin / project_leader
 * （后两个角色是全局范围，不需要团队限定提示）。
 */
export const isTeamLeaderScope = (roles: string[]): boolean =>
  roles.includes('team_leader')
  && !roles.includes('admin')
  && !roles.includes('project_leader');

/** 团队长在资产导入 Tab 顶部看到的范围须知 */
export const TEAM_SCOPE_NOTICE =
  'CSV 的 teamId/teamCode 必须是您领导或所属的团队；越权行会以「权限拒绝：」记入 failedRows 且不会写入库。';
