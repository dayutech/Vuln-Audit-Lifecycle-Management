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
@Table(name = "asset_metadata_extra")
public class AssetMetadataExtra extends BaseAssetMetadata {

    @Column(name = "special_notes", columnDefinition = "TEXT")
    private String specialNotes;
}
