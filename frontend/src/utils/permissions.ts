import type { PermissionVO } from '@/types/role';

/**
 * 权限点模块标签。
 *
 * 权限码格式为 `资源:操作`（示例：`asset:read`、`user:role:write`），
 * 模块名取冒号前第一段，与 `backend/.../security/PermissionCatalog.java` 的模块划分一致。
 */
export const PERMISSION_MODULE_LABELS: Record<string, string> = {
  user: '用户',
  role: '角色',
  permission: '权限点',
  team: '团队',
  asset: '资产',
  vulnerability: '漏洞',
  environment: '环境',
  document: '文档',
  tool: '工具',
  task: '任务',
  knowledge: '知识库',
  notification: '通知',
  dashboard: '仪表盘',
  audit: '审计',
  approval: '审批',
  import: '导入',
};

/** 权限点所属模块（`asset:read` → `asset`） */
export const getPermissionModule = (code: string): string => {
  const index = (code || '').indexOf(':');
  return index > 0 ? code.slice(0, index) : 'other';
};

export const getPermissionModuleLabel = (module: string): string =>
  PERMISSION_MODULE_LABELS[module] ?? module;

export interface PermissionGroup {
  module: string;
  label: string;
  items: PermissionVO[];
}

/**
 * 按模块分组，模块顺序按 PERMISSION_MODULE_LABELS 声明顺序，
 * 未登记的模块排在最后；组内按权限码字典序，保证多次渲染顺序稳定。
 */
export const groupPermissions = (permissions: PermissionVO[]): PermissionGroup[] => {
  const buckets = new Map<string, PermissionVO[]>();
  permissions.forEach((permission) => {
    const module = getPermissionModule(permission.code);
    const list = buckets.get(module) ?? [];
    list.push(permission);
    buckets.set(module, list);
  });

  const knownOrder = Object.keys(PERMISSION_MODULE_LABELS);
  const rank = (module: string) => {
    const index = knownOrder.indexOf(module);
    return index === -1 ? knownOrder.length : index;
  };

  return Array.from(buckets.keys())
    .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
    .map((module) => ({
      module,
      label: getPermissionModuleLabel(module),
      items: buckets
        .get(module)!
        .slice()
        .sort((a, b) => a.code.localeCompare(b.code)),
    }));
};

/** 按权限码/名称/API 路径过滤（大小写不敏感） */
export const filterPermissions = (permissions: PermissionVO[], keyword: string): PermissionVO[] => {
  const text = keyword.trim().toLowerCase();
  if (!text) return permissions;
  return permissions.filter((permission) => {
    const haystack = [permission.code, permission.name, permission.apiPath, permission.description]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(text);
  });
};
