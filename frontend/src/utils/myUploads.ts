/**
 * 「我提交的上传」本地登记（t161）。
 *
 * 后端 t160 只提供 `GET /uploads/pending/{id}`（单条，仅本人可见）与管理员列表，
 * **没有「我的上传」列表端点**。因此前端在提交成功后把 `requestId` 记在本机
 * localStorage（含文件名/大小/提交时间/有效期），「我的上传」面板按这些 id 逐条回查状态。
 *
 * 边界（已在报告写明）：
 * - 换浏览器/清缓存后历史不可见（本地登记丢失）；
 * - 同一账号在另一台设备提交的请求不会出现在这里；
 * - 建议后端补 `GET /uploads/my?status=&page=&size=`，届时本文件可整体退役。
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
/** 本地最多保留 50 条，避免无限增长 */
const MAX_RECORDS = 50;

const safeRead = (): MyUploadRecord[] => {
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

const safeWrite = (records: MyUploadRecord[]) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records.slice(0, MAX_RECORDS)));
  } catch {
    // 隐私模式 / 配额满：静默降级（只影响「我的上传」历史，不影响主流程）
  }
};

export const rememberMyUpload = (record: MyUploadRecord): void => {
  const existing = safeRead().filter((item) => item.requestId !== record.requestId);
  safeWrite([record, ...existing]);
};

export const listMyUploads = (): MyUploadRecord[] => safeRead();

export const forgetMyUpload = (requestId: string): void => {
  safeWrite(safeRead().filter((item) => item.requestId !== requestId));
};
