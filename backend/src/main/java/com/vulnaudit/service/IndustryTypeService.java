package com.vulnaudit.service;

import com.vulnaudit.dto.IndustryTypeAdminVO;
import com.vulnaudit.dto.IndustryTypeOption;
import com.vulnaudit.dto.IndustryTypeUpsertRequest;
import com.vulnaudit.dto.PageResponse;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 行业类型服务（t129 建立，t130 增加管理员 CRUD）
 *
 * <p>内置数据按《国民经济行业分类》(GB/T 4754) 预置门类 + 常见大类（builtin=true）。
 * 管理员只能改内置项的 {@code sortOrder}/{@code enabled}；code/name 与删除都禁止（403）。
 * 删除为**软删**（{@code deleted_at}），避免破坏 {@code customers.industry_code} 的历史引用。
 */
public interface IndustryTypeService {

    /** 内置行业清单（code, name, parentCode） */
    List<String[]> BUILTIN_INDUSTRIES = List.of(
            new String[]{"A", "农、林、牧、渔业", null},
            new String[]{"B", "采矿业", null},
            new String[]{"C", "制造业", null},
            new String[]{"D", "电力、热力、燃气及水生产和供应业", null},
            new String[]{"E", "建筑业", null},
            new String[]{"F", "批发和零售业", null},
            new String[]{"G", "交通运输、仓储和邮政业", null},
            new String[]{"H", "住宿和餐饮业", null},
            new String[]{"I", "信息传输、软件和信息技术服务业", null},
            new String[]{"I65", "软件和信息技术服务业", "I"},
            new String[]{"J", "金融业", null},
            new String[]{"K", "房地产业", null},
            new String[]{"L", "租赁和商务服务业", null},
            new String[]{"M", "科学研究和技术服务业", null},
            new String[]{"N", "水利、环境和公共设施管理业", null},
            new String[]{"O", "居民服务、修理和其他服务业", null},
            new String[]{"P", "教育", null},
            new String[]{"Q", "卫生和社会工作", null},
            new String[]{"R", "文化、体育和娱乐业", null},
            new String[]{"S", "公共管理、社会保障和社会组织", null},
            new String[]{"T", "国际组织", null},
            new String[]{"I64", "互联网和相关服务", "I"}
    );

    /** 启动幂等补齐内置行业（表为空时插入） */
    boolean ensureBuiltin();

    /** 关键字检索（code/name 模糊、大小写不敏感；仅 enabled 且未软删） */
    List<IndustryTypeOption> search(@Nullable String keyword, int limit);

    /** 按 code 取「存在且 enabled」的行业，不存在/禁用抛 400 */
    IndustryTypeOption requireEnabled(@Nullable String code);

    /** 按名称精确匹配（旧前端兼容），未命中返回 null */
    @Nullable
    IndustryTypeOption matchByName(@Nullable String name);

    // ------------------------------------------------------------------ 管理员（t130）

    /** 管理员分页列表：含停用项、排除软删；keyword 匹配 code/name */
    PageResponse<IndustryTypeAdminVO> adminList(@Nullable String keyword, boolean includeDisabled, int page, int size);

    /** 新建自定义行业（builtin=false）；code 重复 409 */
    IndustryTypeAdminVO create(IndustryTypeUpsertRequest request, @Nullable UUID operatorId);

    /** 更新：内置项只允许 sortOrder/enabled；自定义项可改 name/parentCode/sortOrder/enabled */
    IndustryTypeAdminVO update(UUID id, IndustryTypeUpsertRequest request, @Nullable UUID operatorId);

    /** 删除（软删）：仅自定义项；被客户引用 → 409 */
    void delete(UUID id, @Nullable UUID operatorId);
}
