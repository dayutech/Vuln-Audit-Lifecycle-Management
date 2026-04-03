package com.vulnaudit.dto;

import lombok.Data;

/**
 * 资产元数据更新请求DTO
 */
@Data
public class UpsertAssetMetadataRequest {

    private String systemArchitecture;
    private String runtimePlatform;
    private String deploymentMethod;
    private String programmingLanguagesJson;
    private String installPath;
    private String startupScriptPath;
    private String configFilePath;
    private String startupCommand;
    private String programmingLanguage;
    private String framework;
    private String databaseType;
    private String databaseConnection;
    private String adminUsername;
    private String adminPasswordEncrypted;
    private String accountCredentialsJson;
    private String servicePorts;
    private String apiEndpoints;
    private String specialNotes;

}
