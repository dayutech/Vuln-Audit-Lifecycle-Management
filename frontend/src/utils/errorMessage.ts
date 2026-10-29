/**
 * 统一从接口错误中提取「后端 message」。
 *
 * 后端错误响应统一为 { success:false, message:string }；下载类接口用 responseType=blob，
 * 出错时 error.response.data 是 Blob，需要先读文本再解析。调用方拿到的 message
 * 可能是 503 的「当前部署未启用容器编排」、404 的「附件不存在或不属于该漏洞」等，
 * 必须原样展示给用户，不能统一成「操作失败」。
 */

interface ApiErrorLike {
  response?: {
    status?: number;
    data?: unknown;
  };
  message?: string;
}

const parseMessageFromJson = (payload: unknown): string | undefined => {
  if (!payload) {
    return undefined;
  }
  if (typeof payload === 'string') {
    try {
      const parsed = JSON.parse(payload);
      return typeof parsed?.message === 'string' ? parsed.message : undefined;
    } catch {
      return undefined;
    }
  }
  if (typeof payload === 'object') {
    const data = payload as { message?: unknown; error?: { message?: unknown } };
    if (typeof data.message === 'string' && data.message.trim()) {
      return data.message;
    }
    if (typeof data.error?.message === 'string' && data.error.message.trim()) {
      return data.error.message;
    }
  }
  return undefined;
};

/** 同步取错误 message（普通 JSON 响应体） */
export const getApiErrorMessage = (error: unknown, fallback: string): string => {
  const err = error as ApiErrorLike | undefined;
  const parsed = parseMessageFromJson(err?.response?.data);
  if (parsed) {
    return parsed;
  }
  // 网络错误（无 response）时给出可读提示
  if (!err?.response) {
    return err?.message ? `${fallback}：${err.message}` : fallback;
  }
  return fallback;
};

/** 取错误状态码（403/404/503 等，用于分支提示） */
export const getApiErrorStatus = (error: unknown): number | undefined => {
  const err = error as ApiErrorLike | undefined;
  return err?.response?.status;
};

/** 异步取错误 message（responseType=blob 的接口） */
export const getBlobErrorMessage = async (error: unknown, fallback: string): Promise<string> => {
  const err = error as ApiErrorLike | undefined;
  const data = err?.response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = parseMessageFromJson(text);
      if (parsed) {
        return parsed;
      }
    } catch {
      // 读取 Blob 失败则回落
    }
    return fallback;
  }
  const parsed = parseMessageFromJson(data);
  if (parsed) {
    return parsed;
  }
  if (!err?.response) {
    return err?.message ? `${fallback}：${err.message}` : fallback;
  }
  return fallback;
};
