package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 文档创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档创建请求")
public class DocumentCreateRequest {

    @Schema(description = "文档标题")
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题最长200字符")
    private String title;

    @Schema(description = "文档分类")
    @NotBlank(message = "文档分类不能为空")
    private String category;

    @Schema(description = "文档摘要")
    private String summary;

    @Schema(description = "文档内容")
    @NotBlank(message = "文档内容不能为空")
    private String content;

    @Schema(description = "关联资产ID")
    private UUID assetId;

    @Schema(description = "关联任务ID")
    private UUID taskId;
}
