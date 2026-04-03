package com.vulnaudit.dto.metadata;

import lombok.Data;

@Data
public class AssetMetadataEnvRequest {
    private String installPath;
    private String startupScriptPath;
    private String configFilePath;
    private String startupCommand;
    private String databaseType;
    private String databaseConnection;
}
