import request from '@/utils/request';

/**
 * 标签体系（t143 前端，后端 t141）。
 *
 * 两个入口：
 * - `GET  /api/v1/tags`：登录即可，`[{id,name,color,builtin,assetCount}]`；
 *   `assetCount` 由后端按调用者**可见范围**收敛（与 `GET /assets` 同口径），前端不再二次过滤；
 * - `GET/POST/PUT/DELETE /api/v1/admin/tag-definitions`：仅 ADMIN，分页 + keyword + includeDeleted；
 *   内置标签（`builtin=true`）只能改颜色/排序，**改名或删除 → 403**；被资产引用删除 → **409**（消息带引用数）。
 *
 * 写在资产上的标签是 `asset_tags.tag`（名称快照，t141 口径：改名不回填已打标签），
 * 因此列表筛选参数传的是**标签名**而不是 id。
 */

export interface TagOption {
  id: string;
  name: string;
  color?: string | null;
  builtin: boolean;
  assetCount: number;
}

export interface TagDefinition {
  id: string;
  name: string;
  color?: string | null;
  builtin: boolean;
  sortOrder: number;
  deletedAt?: string | null;
}

export interface TagDefinitionQuery {
  keyword?: string;
  includeDeleted?: boolean;
  /** 从 1 开始（内部换算成后端的 0 基） */
  page?: number;
  page_size?: number;
}

export interface TagDefinitionUpsert {
  name?: string;
  color?: string | null;
  sortOrder?: number | null;
}

const unwrap = (res: unknown): any => (res as any)?.data ?? res;

/** 标签下拉（登录即可）。失败返回空数组：列表页筛选不该因为没有标签而报错。 */
export const getTagOptions = async (): Promise<TagOption[]> => {
  try {
    const res = await request.get('/tags', { silent: true });
    const list = unwrap(res);
    if (!Array.isArray(list)) return [];
    return list.map((item: any) => ({
      id: String(item?.id ?? ''),
      name: String(item?.name ?? ''),
      color: item?.color ?? null,
      builtin: Boolean(item?.builtin),
      assetCount: Number(item?.assetCount ?? 0) || 0,
    }));
  } catch {
    return [];
  }
};

/** 单个资产的标签名列表（后端 `GET /assets/{id}/tags` 返回 AssetTag[]，这里只取 tag 名） */
export const getAssetTagNames = async (assetId: string): Promise<string[]> => {
  try {
    const res = await request.get(`/assets/${assetId}/tags`, { silent: true });
    const list = unwrap(res);
    if (!Array.isArray(list)) return [];
    return list
      .map((item: any) => (typeof item === 'string' ? item : item?.tag))
      .filter((tag: unknown): tag is string => typeof tag === 'string' && tag.trim() !== '');
  } catch {
    return [];
  }
};

/**
 * 批量取标签名（t149：改用后端批量端点 `GET /assets/tags?ids=`）。
 *
 * **硬规则（t145）**：列表页**禁止**按行拉 `GET /assets/{id}/tags` —— 一页 20 行就是 20 个请求。
 * 列表接口本身已经内联 `tags`（`AssetVO.tags`），正常情况下**根本不需要调这个函数**；
 * 它只服务于「少量/单个资产的按需刷新」（≤100 个 id，后端超过 100 个直接 400、不可见 id 自动跳过）。
 */
export const getAssetTagNamesBatch = async (assetIds: string[]): Promise<Record<string, string[]>> => {
  const ids = Array.from(new Set(assetIds.filter(Boolean)));
  if (ids.length === 0) return {};
  const result: Record<string, string[]> = {};
  // 后端上限 100：超过时按 100 分组，避免整批 400
  for (let start = 0; start < ids.length; start += 100) {
    const chunk = ids.slice(start, start + 100);
    try {
      const res = await request.get('/assets/tags', { params: { ids: chunk.join(',') }, silent: true });
      const map = unwrap(res);
      if (map && typeof map === 'object') {
        Object.entries(map as Record<string, any>).forEach(([assetId, list]) => {
          result[assetId] = Array.isArray(list)
            ? list.map((item) => (typeof item === 'string' ? item : item?.name)).filter(Boolean)
            : [];
        });
      }
    } catch {
      // 整批失败不影响页面：调用方按"无标签"渲染
    }
  }
  ids.forEach((id) => {
    if (!result[id]) result[id] = [];
  });
  return result;
};

/** 内联标签（`AssetVO.tags` → 标签名数组），列表页直接用它，零额外请求 */
export const inlineTagNames = (tags?: Array<{ name?: string } | string> | null): string[] =>
  Array.isArray(tags)
    ? tags
        .map((item) => (typeof item === 'string' ? item : item?.name))
        .filter((name): name is string => typeof name === 'string' && name.trim() !== '')
    : [];

export const listTagDefinitions = async (query: TagDefinitionQuery = {}) => {
  const res = await request.get('/admin/tag-definitions', {
    params: {
      keyword: query.keyword || undefined,
      includeDeleted: Boolean(query.includeDeleted),
      page: Math.max((query.page ?? 1) - 1, 0),
      size: query.page_size ?? 20,
    },
    silent: true,
  });
  const payload = unwrap(res) ?? {};
  return {
    items: (Array.isArray(payload.content) ? payload.content : []) as TagDefinition[],
    total: Number(payload.totalElements ?? 0) || 0,
  };
};

export const createTagDefinition = async (payload: TagDefinitionUpsert): Promise<TagDefinition> => {
  const res = await request.post('/admin/tag-definitions', payload, { silent: true });
  return unwrap(res) as TagDefinition;
};

export const updateTagDefinition = async (id: string, payload: TagDefinitionUpsert): Promise<TagDefinition> => {
  const res = await request.put(`/admin/tag-definitions/${id}`, payload, { silent: true });
  return unwrap(res) as TagDefinition;
};

/** 软删标签（内置 403；被资产引用 409，消息里带引用数） */
export const deleteTagDefinition = async (id: string): Promise<void> => {
  await request.delete(`/admin/tag-definitions/${id}`, { silent: true });
};

/** 把 `tags`/`tagsAny` 从多选值拼成后端要的逗号分隔字符串 */
export const joinTagNames = (names?: string[]): string | undefined => {
  const clean = (names ?? []).map((name) => name.trim()).filter(Boolean);
  return clean.length > 0 ? Array.from(new Set(clean)).join(',') : undefined;
};

