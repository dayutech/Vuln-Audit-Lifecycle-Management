/**
 * 侧边栏菜单选中态与展开态计算（t89）。
 *
 * 背景：`BasicLayout` 原先用手写数组 `menuKeys` 做匹配，新增「客户管理 / 项目管理 / 审批中心」
 * 时忘了同步，导致这三个路由匹配不到 → `selectedKeys` 退回 `/dashboard`，表现为
 * 「点了但背景不变蓝」。改为**从 menuItems 树里收集 key**（单一事实来源）+ 前缀最长匹配，
 * 以后新增菜单项不会再漏。
 */

export interface MenuLikeItem {
  key?: string | number;
  type?: string;
  children?: MenuLikeItem[];
}

/** 默认高亮项（`/` 与未知路径都归到仪表盘） */
export const DEFAULT_MENU_KEY = '/dashboard';

const isGroup = (item: MenuLikeItem): boolean => item.type === 'group';

/** 收集所有「可点击叶子项」的 key（group 自身的 key 不参与高亮） */
export const collectMenuKeys = (items: MenuLikeItem[]): string[] => {
  const keys: string[] = [];
  const walk = (list: MenuLikeItem[]) => {
    list.forEach((item) => {
      if (Array.isArray(item.children) && item.children.length > 0) {
        // group / submenu：只下钻，不收集自身 key
        walk(item.children);
        return;
      }
      if (item.key !== undefined && !isGroup(item)) {
        keys.push(String(item.key));
      }
    });
  };
  walk(items);
  return keys;
};

const normalizePath = (pathname: string): string => {
  if (!pathname) return '/';
  const trimmed = pathname.split('?')[0].split('#')[0];
  if (trimmed.length > 1 && trimmed.endsWith('/')) return trimmed.slice(0, -1);
  return trimmed;
};

/**
 * 当前路径 → 应高亮的菜单 key。
 *
 * 规则（按优先级）：
 * 1. `/` 或空 → `/dashboard`；
 * 2. 精确命中 → 该 key；
 * 3. 前缀命中（`pathname === `${key}/...`` 才算，避免 `/assets` 误匹配 `/asset-groups`）→ **最长 key 优先**；
 * 4. 都没命中 → `/dashboard`。
 */
export const selectMenuKey = (pathname: string, menuKeys: readonly string[]): string => {
  const path = normalizePath(pathname);
  if (path === '/' || path === '') return DEFAULT_MENU_KEY;

  const keys = Array.from(new Set(menuKeys));
  if (keys.includes(path)) return path;

  const prefixed = keys
    .filter((key) => path.startsWith(`${key}/`))
    .sort((a, b) => b.length - a.length);
  if (prefixed.length > 0) return prefixed[0];

  return DEFAULT_MENU_KEY;
};

/**
 * 需要展开的父级 key（当前菜单项所属的 submenu / 分组祖先）。
 *
 * 现有菜单用 antd 的 `type: 'group'`（分组恒展开，没有 openKeys 概念），
 * 本函数为将来改用 `SubMenu` 预留：只返回**非 group** 的父级 key，
 * 因此当前实现返回空数组，不会影响分组展示。
 */
export const collectOpenKeys = (items: MenuLikeItem[], activeKey: string): string[] => {
  const result: string[] = [];
  const walk = (list: MenuLikeItem[], ancestors: string[]): boolean => {
    for (const item of list) {
      const key = item.key !== undefined ? String(item.key) : '';
      if (Array.isArray(item.children) && item.children.length > 0) {
        const nextAncestors = isGroup(item) ? ancestors : [...ancestors, key];
        if (walk(item.children, nextAncestors)) {
          if (!isGroup(item)) result.push(key);
          return true;
        }
        continue;
      }
      if (key === activeKey) {
        result.push(...ancestors);
        return true;
      }
    }
    return false;
  };
  walk(items, []);
  return Array.from(new Set(result.filter(Boolean)));
};
