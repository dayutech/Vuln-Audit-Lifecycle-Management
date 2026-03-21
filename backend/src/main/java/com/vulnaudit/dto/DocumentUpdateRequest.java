package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档更新请求")
public class DocumentUpdateRequest {

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档分类")
    private String category;

    @Schema(description = "文档摘要")
    private String summary;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "变更说明")
    private String changelog;
}
