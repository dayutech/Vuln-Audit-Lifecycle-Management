package com.vulnaudit.service;

import com.vulnaudit.dto.asset.AssetDTO;
import com.vulnaudit.entity.AssetEntity;
import com.vulnaudit.exception.ResourceNotFoundException;
import com.vulnaudit.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public Page<AssetEntity> findAll(Pageable pageable) {
        return assetRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AssetEntity> findByStatus(AssetEntity.AssetStatus status, Pageable pageable) {
        return assetRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public AssetEntity findById(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id.toString()));
    }

    @Transactional
    public AssetEntity create(AssetDTO dto) {
        AssetEntity asset = AssetEntity.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .type(dto.getType())
                .status(AssetEntity.AssetStatus.available)
                .priority(dto.getPriority())
                .description(dto.getDescription())
                .url(dto.getUrl())
                .build();
        return assetRepository.save(asset);
    }

    @Transactional
    public AssetEntity update(UUID id, AssetDTO dto) {
        AssetEntity asset = findById(id);
        asset.setName(dto.getName());
        asset.setCode(dto.getCode());
        asset.setType(dto.getType());
        asset.setPriority(dto.getPriority());
        asset.setDescription(dto.getDescription());
        asset.setUrl(dto.getUrl());
        return assetRepository.save(asset);
    }

    @Transactional
    public void delete(UUID id) {
        AssetEntity asset = findById(id);
        assetRepository.delete(asset);
    }

    @Transactional(readOnly = true)
    public Page<AssetEntity> search(String keyword, Pageable pageable) {
        return assetRepository.searchByKeyword(keyword, pageable);
    }
}
