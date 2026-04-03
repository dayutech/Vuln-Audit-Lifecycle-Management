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
@Table(name = "asset_metadata_account")
public class AssetMetadataAccount extends BaseAssetMetadata {

    @Column(name = "admin_username", length = 100)
    private String adminUsername;

    @Column(name = "admin_password_encrypted", length = 255)
    private String adminPasswordEncrypted;

    @Column(name = "account_credentials_json", columnDefinition = "TEXT")
    private String accountCredentialsJson;
}
