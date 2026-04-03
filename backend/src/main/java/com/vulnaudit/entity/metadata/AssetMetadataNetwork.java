package com.vulnaudit.entity.metadata;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_metadata_network")
public class AssetMetadataNetwork extends BaseAssetMetadata {

    @Column(name = "service_ports", columnDefinition = "TEXT")
    private String servicePorts;

    @Column(name = "api_endpoints", columnDefinition = "TEXT")
    private String apiEndpoints;
}
