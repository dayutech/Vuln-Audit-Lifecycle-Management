package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.IndustryTypeOption;
import com.vulnaudit.service.IndustryTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行业类型检索（t129）：登录即可访问，供「边输入边检索」的下拉使用。
 */
@RestController
@RequestMapping("/api/v1/industry-types")
@RequiredArgsConstructor
@Tag(name = "行业类型", description = "GB/T 4754 行业分类检索")
public class IndustryTypeController {

    private final IndustryTypeService industryTypeService;

    @GetMapping
    @Operation(summary = "行业检索", description = "按 code/name 模糊匹配（大小写不敏感），仅返回启用项；limit 上限 100")
    public ApiResponse<List<IndustryTypeOption>> search(
            @Parameter(description = "关键字（code 或名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "返回数量，默认 20，最大 100") @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(industryTypeService.search(keyword, limit));
    }
}
