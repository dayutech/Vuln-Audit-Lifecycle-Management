package com.vulnaudit.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AssetVersionCreateRequest {
    private String name;
    private String description;
    private String status;
    private Boolean inheritBaseOverview;
    private UUID inheritFromVersionId;
}
