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
@Table(name = "asset_metadata_env")
public class AssetMetadataEnv extends BaseAssetMetadata {

    @Column(name = "install_path", length = 500)
    private String installPath;

    @Column(name = "startup_script_path", columnDefinition = "TEXT")
    private String startupScriptPath;

    @Column(name = "config_file_path", columnDefinition = "TEXT")
    private String configFilePath;

    @Column(name = "startup_command", length = 1000)
    private String startupCommand;

    @Column(name = "database_type", length = 50)
    private String databaseType;

    @Column(name = "database_connection", length = 500)
    private String databaseConnection;
}
