package com.vulnaudit.exception;

/**
 * 请求过于频繁异常（如登录失败次数超限）。
 *
 * <p>由全局异常处理器映射为 429，并在响应头回写 {@code Retry-After}。
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 0);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
