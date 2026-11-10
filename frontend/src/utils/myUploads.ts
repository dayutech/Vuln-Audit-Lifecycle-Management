/**
 * 「我提交的上传」本地登记（t161 建立，t164 改为**内存优先**）。
 *
 * 后端 t160 只提供 `GET /uploads/pending/{id}`（单条、仅本人可见）与管理员列表，
 * **没有「我的上传」列表端点**；因此前端需要自己记录 requestId。
 *
 * t164 修正：
 * - **内存（模块级数组）为主数据源** —— 上传成功那一刻就写入，不依赖 localStorage 是否可用
 *   （隐私模式 / 配额满 / 被清缓存都不会导致「刚传完却查不到」）；
 * - localStorage 仅作**跨刷新**的补充持久化，读写失败静默降级；
 * - 读取时按 requestId 去重、内存项优先（保证最新状态）。
 *
 * 边界（已写进报告）：换浏览器/清缓存后历史仅剩后端未提供列表这段空档；同一账号在别的设备
 * 提交的请求不会出现在这里。后端补 `GET /uploads/my` 后本文件可整体退役。
 */
export interface MyUploadRecord {
  requestId: string;
  fileName: string;
  fileSize?: number | null;
  targetType: string;
  requestedAt: string;
  purgeAfter?: string | null;
}

const STORAGE_KEY = 'upload.pending.registry.v1';
/** 历史上 t163 真机脚本探测过的旧键，挂载时清理一次即可（本文件不再写入） */
const LEGACY_KEYS = ['owl_my_uploads'];
const MAX_RECORDS = 50;

/** 会话内存登记：唯一可信来源（上传成功即写入） */
let memoryStore: MyUploadRecord[] = [];

const readStorage = (): MyUploadRecord[] => {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is MyUploadRecord => Boolean(item && item.requestId));
  } catch {
    return [];
  }
};

const writeStorage = (records: MyUploadRecord[]) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records.slice(0, MAX_RECORDS)));
  } catch {
    // 隐私模式 / 配额满：仅失去「跨刷新」能力，内存登记仍然可用
  }
};

/** 合并：内存优先，其次 localStorage，按 requestId 去重，按提交时间倒序 */
const mergeRecords = (memory: MyUploadRecord[], stored: MyUploadRecord[]): MyUploadRecord[] => {
  const map = new Map<string, MyUploadRecord>();
  stored.forEach((item) => map.set(item.requestId, item));
  memory.forEach((item) => map.set(item.requestId, item));
  return Array.from(map.values()).sort((a, b) => (a.requestedAt < b.requestedAt ? 1 : -1));
};

export const rememberMyUpload = (record: MyUploadRecord): void => {
  memoryStore = [record, ...memoryStore.filter((item) => item.requestId !== record.requestId)].slice(0, MAX_RECORDS);
  writeStorage(mergeRecords(memoryStore, readStorage()));
};

/** 读取：内存 + 持久化合并（内存优先） */
export const listMyUploads = (): MyUploadRecord[] => mergeRecords(memoryStore, readStorage());

export const forgetMyUpload = (requestId: string): void => {
  memoryStore = memoryStore.filter((item) => item.requestId !== requestId);
  writeStorage(mergeRecords(memoryStore, readStorage()).filter((item) => item.requestId !== requestId));
};

/** 清理历史版本用过的键（只删不写） */
export const cleanupLegacyUploadKeys = (): void => {
  LEGACY_KEYS.forEach((key) => {
    try {
      window.localStorage.removeItem(key);
    } catch {
      /* ignore */
    }
  });
};
