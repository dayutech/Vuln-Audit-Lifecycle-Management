package com.vulnaudit.exception;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.i18n.ApiMessageResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * 功能说明：
 * - 统一处理所有异常
 * - 返回标准化的错误响应
 * - 记录异常日志
 * - 处理参数校验异常
 * - 处理安全相关异常
 *
 * <p>i18n（t60）：所有对外文案统一经 {@link ApiMessageResolver} 解析 ——
 * {@code Accept-Language: en}（或 {@code ?lang=en}）返回英文；未传时返回中文（与既有行为一致）。
 * 未纳入 i18n 的消息码回退到 {@link ErrorCode#getMessage()} 的中文文案，不抛异常。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiMessageResolver messageResolver;

    /**
     * 处理业务异常
     * 
     * @param e 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        
        int code = e.getErrorCode().getCode();
        HttpStatus status;

        // 业务错误码区间：1000-2999（用户/角色/资产/漏洞/文档/环境/工具/任务/知识库/通知/团队）
        // 说明（t65）：此前上限写成 < 2000，团队域错误码（2001-2003）会落到 else 分支，
        // 被 clamp 成 599 并触发 HttpStatus.valueOf 抛 IllegalArgumentException → 500。
        if (code >= 1000 && code < 3000) {
            if (code == 1003 || code == 1201 || code == 1301 || code == 1401 || 
                code == 1501 || code == 1601 || code == 1701 || code == 1801 ||
                code == 1901 || code == 2001 || code == 1205 || code == 1206 ||
                code == 1101 || code == 1103) {
                status = HttpStatus.NOT_FOUND;
            } else if (code == 1004 || code == 1008 || code == 1006) {
                status = HttpStatus.UNAUTHORIZED;
            } else if (code == 1005) {
                status = HttpStatus.FORBIDDEN;
            } else if (code == 1001 || code == 1002 || code == 1102 || code == 1104 ||
                       code == 1204 || code == 2002 || code == 2003) {
                status = HttpStatus.CONFLICT;
            } else if (code == 1009 || code == 1010) {
                status = HttpStatus.BAD_REQUEST;
            } else {
                status = HttpStatus.BAD_REQUEST;
            }
        } else {
            // 形如 4xx/5xx 的业务码；非法值不得再抛异常（否则变成 500 掩盖真实原因）
            int candidate = Math.min(Math.max(code, 400), 599);
            HttpStatus resolved = HttpStatus.resolve(candidate);
            status = resolved != null ? resolved : HttpStatus.BAD_REQUEST;
        }
        
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(e.getErrorCode(), e.getMessage())));
    }

    /**
     * 处理参数校验异常（@Valid注解）
     * 
     * @param e 参数校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            // 校验注解的 message 可能是消息码（validation.*），也可能已是写好的文案 → 解析或原样保留
            String errorMessage = messageResolver.resolveOrKeep(error.getDefaultMessage());
            errors.put(fieldName, errorMessage);
        });
        log.warn("参数校验失败: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(messageResolver.resolve(ErrorCode.VALIDATION_ERROR.messageKey(), "参数校验失败"))
                        .data(errors)
                        .build());
    }

    /**
     * 处理约束违反异常（@Validated注解）
     * 
     * @param e 约束违反异常
     * @return 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException e) {
        Map<String, String> errors = e.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> messageResolver.resolveOrKeep(violation.getMessage())
                ));
        log.warn("约束违反: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(messageResolver.resolve(ErrorCode.VALIDATION_ERROR.messageKey(), "参数校验失败"))
                        .data(errors)
                        .build());
    }

    /**
     * 处理认证异常
     * 
     * @param e 认证异常
     * @return 错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(ErrorCode.UNAUTHORIZED, null)));
    }

    /**
     * 处理凭证错误异常
     * 
     * @param e 凭证错误异常
     * @return 错误响应
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("凭证错误: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(ErrorCode.PASSWORD_ERROR, null)));
    }

    /**
     * 处理访问拒绝异常
     * 
     * @param e 访问拒绝异常
     * @return 错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(ErrorCode.FORBIDDEN, null)));
    }

    /**
     * 处理请求方法不支持异常
     * 
     * @param e 请求方法不支持异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(messageResolver.resolve("error.method_not_allowed", ErrorCode.METHOD_NOT_ALLOWED.getMessage())));
    }

    /**
     * 处理缺失必填参数异常。
     *
     * <p>此前会落到兜底的 {@link #handleException(Exception)}，被当成系统异常返回 500，
     * 既误导调用方也会产生 ERROR 级噪音日志。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(
            MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("缺少必填参数: " + e.getParameterName()));
    }

    /**
     * 处理参数类型不匹配异常（如 UUID 位置传了非法字符串）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("参数格式错误: " + e.getName()));
    }

    /**
     * 处理请求体不可读异常（JSON 语法错误 / 枚举值非法等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("请求体格式错误，请检查 JSON 结构与字段类型"));
    }

    /**
     * 处理上传超过大小限制异常。
     *
     * <p>必须声明得比 {@link MultipartException} 更具体，否则会被通用多部分异常处理器
     * 抢走并返回误导性的“文件格式错误”。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e) {
        log.warn("上传文件超出限制: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("上传文件超过大小限制（单文件最大 100MB）"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(Exception e) {
        log.warn("上传请求格式错误: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("上传文件格式错误，请重新选择文件"));
    }

    /**
     * 处理限流异常（登录失败次数超限等），返回 429 并附 Retry-After 头。
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException e) {
        log.warn("请求被限流: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 处理404异常
     * 
     * @param e 404异常
     * @return 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("资源未找到: {}", e.getRequestURL());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(ErrorCode.NOT_FOUND, null)));
    }

    /**
     * 处理所有未捕获的异常
     * 
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(messageResolver.resolveForErrorCode(ErrorCode.INTERNAL_ERROR, null)));
    }

}
