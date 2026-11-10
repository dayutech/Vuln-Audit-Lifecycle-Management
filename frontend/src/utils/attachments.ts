/**
 * 知识库 / 规范文档的手动上传辅助（t159，需求 15）。
 *
 * ## 为什么是"前端把文件内容编进记录"
 * 后端的 `KnowledgeArticle` / `Document` **都没有附件表或附件字段**，也没有通用的
 * 「上传文件 → 返回附件 id」端点（现有上传都绑在资产/漏洞/工具/导入上），
 * 而本任务的约束是**不新增后端依赖**。因此这里采用：
 *
 * 1. 文本类（`.md` / `.txt`）：文件文本直接作为记录正文；
 * 2. 其余类型（`.pdf` / `.docx` / `.xlsx` / `.pptx` / `.zip`）以及文本原件：
 *    以 **base64 附件块** 追加在正文末尾（`<!--owl-attachment:{...}-->`），
 *    下载时前端还原成 Blob（文件名/MIME 保留），因此"附件可下载"是真的；
 * 3. 受 `content` 列为 **TEXT(64 KB)** 限制，附件 base64 后必须很小；
 *    这里把**二进制附件限制在 40 KB 以内**，超过则给出明确提示，
 *    并建议先上传到资产文件/漏洞附件后在正文里引用链接。
 *
 * 后端若新增 `POST /knowledge/attachments` / `POST /documents/attachments`（返回 url），
 * 只需把 `buildAttachmentBlock` / `downloadAttachment` 换成"上传拿 url + `<a href>`"即可，
 * 其余页面无需改动。
 */
export const ATTACHMENT_ACCEPT = '.md,.txt,.pdf,.docx,.xlsx,.pptx,.zip';

/** 单个二进制附件的大小上限（base64 后约 54 KB，仍低于 TEXT 的 64 KB） */
export const BINARY_ATTACHMENT_MAX_BYTES = 40 * 1024;
/** 文本文件直接作为正文时的大小上限（避免正文本身撑爆 TEXT 列） */
export const TEXT_ATTACHMENT_MAX_BYTES = 60 * 1024;

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

export interface AttachmentMeta {
  name: string;
  size: number;
  mime: string;
  data: string;
}

const BLOCK_RE = /<!--owl-attachment:([\s\S]*?)-->/;

/** 构造附件块（base64），用于追加到正文末尾 */
export const buildAttachmentBlock = async (file: File): Promise<string> => {
  const buffer = await file.arrayBuffer();
  let binary = '';
  const bytes = new Uint8Array(buffer);
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + chunk)) as unknown as number[]);
  }
  const meta: AttachmentMeta = {
    name: file.name,
    size: file.size,
    mime: file.type || MIME_BY_EXT[extensionOf(file.name)] || 'application/octet-stream',
    data: window.btoa(binary),
  };
  return `\n\n<!--owl-attachment:${JSON.stringify(meta)}-->`;
};

/** 从正文里解析附件元数据；没有则返回 null */
export const parseAttachment = (content?: string | null): AttachmentMeta | null => {
  if (!content) return null;
  const hit = BLOCK_RE.exec(content);
  if (!hit) return null;
  try {
    const parsed = JSON.parse(hit[1]) as AttachmentMeta;
    return parsed && typeof parsed.data === 'string' ? parsed : null;
  } catch {
    return null;
  }
};

/** 去掉附件块后的正文（用于编辑器展示与预览） */
export const stripAttachment = (content?: string | null): string =>
  (content ?? '').replace(BLOCK_RE, '').trimEnd();

/** 触发浏览器下载：优先还原附件块，其次把正文导出为 .md */
export const downloadRecordFile = (content: string | null | undefined, fallbackName: string) => {
  const attachment = parseAttachment(content);
  const blob = attachment
    ? (() => {
        const binary = window.atob(attachment.data);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
        return new Blob([bytes], { type: attachment.mime || 'application/octet-stream' });
      })()
    : new Blob([stripAttachment(content) || ''], { type: 'text/markdown;charset=utf-8' });
  const name = attachment?.name || `${fallbackName}.md`;
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};

/** 读取文本文件内容（文本类上传用） */
export const readTextFile = (file: File): Promise<string> => {
  if (typeof file.text === 'function') return file.text();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
};

/** 默认标题：去掉扩展名的文件名 */
export const titleFromFileName = (name: string): string => {
  const dot = name.lastIndexOf('.');
  return (dot > 0 ? name.slice(0, dot) : name).slice(0, 200);
};
