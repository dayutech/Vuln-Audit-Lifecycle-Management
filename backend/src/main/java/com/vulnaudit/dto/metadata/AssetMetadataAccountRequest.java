package com.vulnaudit.dto.metadata;

import lombok.Data;

@Data
public class AssetMetadataAccountRequest {
    private String adminUsername;
    private String adminPasswordEncrypted;
    private String accountCredentialsJson;
}
