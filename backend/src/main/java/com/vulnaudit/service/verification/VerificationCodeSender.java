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
}
