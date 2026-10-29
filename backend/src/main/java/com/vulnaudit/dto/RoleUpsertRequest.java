package com.vulnaudit.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 角色创建/更新请求。
 *
 * <p>对应 {@code docs/design/api_design.md} 11.4 / 11.5：创建或更新角色时可一并提交
 * {@code permission_ids} 完成权限点分配，避免前端为了保存一次表单连调两个接口。
 *
 * <p>字段同时兼容两种命名：后端序列化习惯的 {@code permissionIds} 与设计文档里的
 * {@code permission_ids}（通过 {@link JsonAlias} 接受）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpsertRequest {

    /** 角色名称 */
    private String name;

    /** 角色编码（更新时系统内置角色不可修改） */
    private String code;

    /** 角色描述 */
    private String description;

    /** 权限点ID集合；为 {@code null} 表示本次不改动已有权限关联 */
    @JsonAlias("permission_ids")
    private List<UUID> permissionIds;
}
