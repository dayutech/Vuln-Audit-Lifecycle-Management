package com.vulnaudit.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 
 * 功能说明：
 * - 继承RuntimeException，为非检查异常
 * - 包含错误码和错误消息
 * - 用于业务逻辑中抛出可预期的异常
 * 
 * 使用示例：
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户ID: " + userId);
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final ErrorCode errorCode;

    /**
     * 构造业务异常
     * 
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（带自定义消息）
     * 
     * @param errorCode 错误码
     * @param message 自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（带原因）
     * 
     * @param errorCode 错误码
     * @param cause 异常原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（带自定义消息和原因）
     * 
     * @param errorCode 错误码
     * @param message 自定义错误消息
     * @param cause 异常原因
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取HTTP状态码
     * 
     * @return HTTP状态码
     */
    public Integer getCode() {
        return errorCode.getCode();
    }

}
