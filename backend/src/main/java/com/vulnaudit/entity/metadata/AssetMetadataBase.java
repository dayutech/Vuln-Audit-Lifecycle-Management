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
@Table(name = "asset_metadata_base")
public class AssetMetadataBase extends BaseAssetMetadata {

    @Column(name = "system_architecture", length = 50)
    private String systemArchitecture;

    @Column(name = "runtime_platform")
    private String runtimePlatform;

    @Column(name = "deployment_method")
    private String deploymentMethod;

    @Column(name = "programming_languages_json", columnDefinition = "TEXT")
    private String programmingLanguagesJson;

    @Column(name = "programming_language", length = 100)
    private String programmingLanguage;

    @Column(name = "framework", length = 100)
    private String framework;

    @Column(name = "copied_from_asset_version_id")
    private java.util.UUID copiedFromAssetVersionId;
}
