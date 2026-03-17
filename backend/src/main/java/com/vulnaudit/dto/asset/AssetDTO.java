package com.vulnaudit.dto.asset;

import com.vulnaudit.entity.AssetEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetDTO {
    @NotBlank(message = "资产名称不能为空")
    private String name;
    private String code;
    private AssetEntity.AssetType type;
    private AssetEntity.AssetPriority priority;
    private String description;
    private String url;
}
