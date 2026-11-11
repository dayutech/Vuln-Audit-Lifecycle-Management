/**
 * 知识库 / 规范文档的附件辅助（t174 重写）。
 *
 * ## 重大变更（相对 t159 的临时方案）
 * t159 因为后端没有附件模型，把文件以 **base64 塞进正文**（`<!--owl-attachment:…-->`），
 * 受 `content TEXT(64KB)` 限制实际传不了 PDF/PPT/压缩包。t162 已提供真正的附件端点，
 * 本文件因此**删除**了 `buildAttachmentBlock` / `parseAttachment`（base64 解析）/
 * `downloadRecordFile`（前端还原 Blob）等全部临时逻辑，只保留：
 *
 * - 上传白名单与前缀（`ATTACHMENT_ACCEPT`）与 50MB 上限（与后端一致）；
 * - 文件名 → 默认标题；
 * - **历史数据只读兼容**：`hasLegacyAttachment()` / `stripAttachment()` 仅用于「旧格式附件
 *   （不可下载）」提示与展示清理，不做任何转换、不改写正文。
 */
export const ATTACHMENT_ACCEPT = '.md,.txt,.pdf,.docx,.xlsx,.pptx,.zip';

/** 与后端一致的单文件上限（50 MB） */
export const ATTACHMENT_MAX_BYTES = 50 * 1024 * 1024;

const MIME_BY_EXT: Record<string, string> = {
  md: 'text/markdown',
  txt: 'text/plain',
  pdf: 'application/pdf',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  zip: 'application/zip',
};

export const extensionOf = (name: string): string => {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
};

export const isAllowedAttachment = (name: string): boolean =>
  ATTACHMENT_ACCEPT.split(',').includes(`.${extensionOf(name)}`);

export const mimeOf = (name: string): string => MIME_BY_EXT[extensionOf(name)] ?? 'application/octet-stream';

/** 默认标题：去掉扩展名的文件名 */
export const titleFromFileName = (name: string): string => {
  const dot = name.lastIndexOf('.');
  return (dot > 0 ? name.slice(0, dot) : name).slice(0, 200);
};

export const formatFileSize = (bytes?: number | null): string => {
  if (bytes === null || bytes === undefined) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
};

/** 旧格式（t159 base64）附件块标记 */
const LEGACY_BLOCK_RE = /<!--owl-attachment:[\s\S]*?-->/;

/** 历史记录正文里是否还残留 base64 附件块（只读提示用，不转换） */
export const hasLegacyAttachment = (content?: string | null): boolean =>
  Boolean(content && LEGACY_BLOCK_RE.test(content));

/** 展示用：把旧格式附件块从正文里去掉（**不改动数据**，仅渲染时裁剪） */
export const stripAttachment = (content?: string | null): string =>
  (content ?? '').replace(LEGACY_BLOCK_RE, '').trimEnd();
