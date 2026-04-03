package com.vulnaudit.dto.metadata;

import lombok.Data;

@Data
public class AssetMetadataBaseRequest {
    private String systemArchitecture;
    private String runtimePlatform;
    private String deploymentMethod;
    private String programmingLanguagesJson;
    private String programmingLanguage;
    private String framework;
}
