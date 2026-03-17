package com.vulnaudit.vo;

import com.vulnaudit.entity.AssetEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetVO {
    private UUID id;
    private String name;
    private String code;
    private AssetEntity.AssetType type;
    private AssetEntity.AssetStatus status;
    private AssetEntity.AssetPriority priority;
    private String description;
    private String url;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
