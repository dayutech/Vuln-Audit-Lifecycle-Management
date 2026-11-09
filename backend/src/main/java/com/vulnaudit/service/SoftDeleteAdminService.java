package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.SoftDeleteAdminItem;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 软删管理（t140）：管理端「含已删」列表 + 恢复
 *
 * <p>普通列表/详情/统计走实体上的 {@code @SQLRestriction("deleted_at IS NULL")}（默认不可见）；
 * 只有这里（ADMIN）能看到已删行并把它们恢复。
 */
public interface SoftDeleteAdminService {

    /** 软删对象种类（客户 / 项目 / 资产） */
    enum Kind {
        CUSTOMER, PROJECT, ASSET
    }

    /**
     * 管理端列表
     *
     * @param kind           种类
     * @param keyword        编码/名称关键字（空串表示不过滤）
     * @param includeDeleted true = 含已删行（默认 false）
     * @param page           页码（0 起）
     * @param size           每页大小
     */
    PageResponse<SoftDeleteAdminItem> page(Kind kind, @Nullable String keyword, boolean includeDeleted,
                                           int page, int size);

    /**
     * 恢复（清 deleted_at / deleted_by）
     *
     * <p>幂等：未删时同样返回成功（不重复写审计）；对象根本不存在 → 404。
     *
     * @return true = 本次真的恢复了（false = 本来就未删）
     */
    boolean restore(Kind kind, @Nullable UUID id, @Nullable UUID operatorId);
}
