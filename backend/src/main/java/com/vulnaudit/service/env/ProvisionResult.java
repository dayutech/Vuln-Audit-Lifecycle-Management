package com.vulnaudit.service.env;

/**
 * 环境部署（容器编排）操作结果
 *
 * @param success     是否成功
 * @param containerId 容器ID（成功时返回）
 * @param message     结果说明（失败时为失败原因）
 */
public record ProvisionResult(boolean success, String containerId, String message) {

    public static ProvisionResult ok(String containerId) {
        return new ProvisionResult(true, containerId, "容器已就绪");
    }

    public static ProvisionResult fail(String message) {
        return new ProvisionResult(false, null, message);
    }

    /**
     * 是否因「未启用容器编排」而失败
     */
    public boolean unavailable() {
        return !success && message != null && message.startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE);
    }
}
