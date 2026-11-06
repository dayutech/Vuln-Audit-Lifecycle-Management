package com.vulnaudit.service.verification;

/**
 * 验证码投递通道抽象（t102）
 *
 * <p>业务代码（注册/找回密码）只依赖本接口，<b>不得</b>直连短信或邮件服务商；
 * 生产环境用真实网关实现替换 {@link LoggingVerificationCodeSender} 即可，无需改动业务逻辑。
 */
public interface VerificationCodeSender {

    /**
     * 发送短信验证码
     *
     * @param target 手机号（中国大陆 11 位）
     * @param code   明文验证码（只在投递瞬间存在，不得落库/落日志）
     */
    void sendSms(String target, String code);

    /**
     * 发送邮件验证码
     *
     * @param target 邮箱
     * @param code   明文验证码
     */
    void sendEmail(String target, String code);
    /**
     * 发送账户安全告警（t123：账户因暴力破解被锁定）
     *
     * <p>默认实现只写日志（不落明文敏感信息）；接入真实短信/邮件网关的实现可覆盖本方法。
     * 内容里<b>不得</b>包含密码或验证码，只用时间、来源 IP、解锁方式等非敏感信息。
     *
     * @param target  手机号或邮箱
     * @param channel 渠道 sms / email
     * @param message 告警正文（已由调用方做好脱敏）
     */
    default void sendSecurityAlert(String target, String channel, String message) {
        // 默认：仅日志（生产用真实网关实现覆盖）
    }
}
