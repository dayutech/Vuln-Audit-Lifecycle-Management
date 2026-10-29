/**
 * 文件下载工具：解析 Content-Disposition 文件名（RFC 5987 filename*），
 * 并用 a[download] 触发浏览器落盘。
 */

/** 解析 Content-Disposition，优先 filename*（UTF-8''percent-encoded），回落 filename */
export const parseContentDispositionFilename = (header?: string | null): string | undefined => {
  if (!header) {
    return undefined;
  }
  // filename*=UTF-8''%E4%B8%AD%E6%96%87.txt（也兼容小写 filename* 与带引号写法）
  const starMatch = /filename\*\s*=\s*([^;]+)/i.exec(header);
  if (starMatch) {
    let value = starMatch[1].trim().replace(/^["']|["']$/g, '');
    const charsetSeparator = value.indexOf("''");
    if (charsetSeparator >= 0) {
      value = value.slice(charsetSeparator + 2);
    } else if (/^[^']*'/.test(value)) {
      value = value.split("'").slice(2).join("'");
    }
    try {
      return decodeURIComponent(value);
    } catch {
      // 非法 percent-encoding 时回落 filename
    }
  }
  const plainMatch = /filename\s*=\s*("?)([^";]+)\1/i.exec(header);
  if (plainMatch) {
    return plainMatch[2].trim();
  }
  return undefined;
};

/** 触发浏览器下载；返回实际使用的文件名 */
export const saveBlobAsFile = (blob: Blob, fileName: string): string => {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName || 'download.bin';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // 下载触发后再释放，避免部分浏览器取消下载
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 2000);
  return link.download;
};
