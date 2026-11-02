package com.vulnaudit.dto;

import java.util.UUID;

/**
 * 客户摘要（嵌入项目视图，t68）
 */
public record CustomerBrief(UUID id, String code, String name) {
}
