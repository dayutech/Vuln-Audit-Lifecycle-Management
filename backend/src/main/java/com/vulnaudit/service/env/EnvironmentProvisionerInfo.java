package com.vulnaudit.service.env;

/**
 * 环境部署器运行信息（用于前端提示）
 *
 * @param type    当前生效的部署器类型：docker / noop
 * @param available 是否可用
 * @param message 说明（不可用时包含原因）
 */
public record EnvironmentProvisionerInfo(String type, boolean available, String message) {
}
