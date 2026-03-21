package com.vulnaudit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应类
 * 
 * 功能说明：
 * - 统一分页数据响应格式
 * - 包含分页信息（当前页、每页大小、总数、总页数）
 * - 支持泛型，可用于任意类型的数据列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "数据列表")
    private List<T> content;

    @Schema(description = "总记录数", example = "100")
    private Long totalElements;

    @Schema(description = "总页数", example = "10")
    private Integer totalPages;

    @Schema(description = "当前页码（从0开始）", example = "0")
    private Integer currentPage;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize;

    /**
     * 创建分页响应
     */
    public static <T> PageResponse<T> of(List<T> items, Integer page, Integer pageSize, Long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PageResponse.<T>builder()
                .content(items)
                .currentPage(page)
                .pageSize(pageSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    /**
     * 创建空的分页响应
     */
    public static <T> PageResponse<T> empty(Integer page, Integer pageSize) {
        return PageResponse.<T>builder()
                .content(List.of())
                .currentPage(page)
                .pageSize(pageSize)
                .totalElements(0L)
                .totalPages(0)
                .build();
    }

}
