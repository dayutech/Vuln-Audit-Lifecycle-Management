import request from '@/utils/request';

/**
 * 行业类型（t129 后端 `GET /industry-types`，t142 前端首次接入）。
 *
 * 用途：客户表单的「行业」字段做**检索**（关键字匹配 code / 名称），
 * 同时保留自由输入能力（AutoComplete），不会把存量自由文本行业锁死。
 */
export interface IndustryTypeOption {
  code: string;
  name: string;
  parentCode?: string | null;
}

/** 登录即可读；只返回 enabled 且未软删的项（后端已处理） */
export const searchIndustryTypes = (keyword?: string, limit = 20): Promise<any> => {
  return request.get('/industry-types', {
    params: { keyword: keyword || undefined, limit },
    silent: true,
  } as any);
};

export const extractIndustryOptions = (res: any): IndustryTypeOption[] => {
  const payload = res?.data ?? res;
  const list = Array.isArray(payload) ? payload : (payload?.content ?? payload?.items ?? []);
  if (!Array.isArray(list)) return [];
  return list
    .map((item: any) => ({ code: String(item?.code ?? ''), name: String(item?.name ?? ''), parentCode: item?.parentCode ?? null }))
    .filter((item) => item.code && item.name);
};
