package com.vulnaudit.dto;

import java.util.UUID;

/**
 * 项目摘要（t69，嵌入资产视图的 projects 数组）
 *
 * <p>{@code customerId} 让前端在只有项目摘要时也能把项目归到客户下，无需再查一次。
 */
public record ProjectBrief(UUID id, String code, String name, UUID customerId) {
}
