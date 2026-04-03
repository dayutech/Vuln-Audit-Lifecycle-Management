package com.vulnaudit.dto.metadata;

import lombok.Data;

@Data
public class AssetMetadataNetworkRequest {
    private String servicePorts;
    private String apiEndpoints;
}
