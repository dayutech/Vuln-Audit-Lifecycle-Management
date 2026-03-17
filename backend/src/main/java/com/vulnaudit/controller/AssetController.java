package com.vulnaudit.controller;

import com.vulnaudit.dto.asset.AssetDTO;
import com.vulnaudit.entity.AssetEntity;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.vo.AssetVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<Page<AssetVO>> list(
            @RequestParam(required = false) AssetEntity.AssetStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<AssetEntity> assets = status != null ?
                assetService.findByStatus(status, pageable) :
                assetService.findAll(pageable);

        return ResponseEntity.ok(assets.map(this::toVO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetVO> getById(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(toVO(assetService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<AssetVO> create(@Valid @RequestBody AssetDTO dto) {
        AssetEntity asset = assetService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toVO(asset));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssetVO> update(@PathVariable java.util.UUID id, @Valid @RequestBody AssetDTO dto) {
        AssetEntity asset = assetService.update(id, dto);
        return ResponseEntity.ok(toVO(asset));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable java.util.UUID id) {
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AssetVO toVO(AssetEntity entity) {
        return AssetVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .type(entity.getType())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .description(entity.getDescription())
                .url(entity.getUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
