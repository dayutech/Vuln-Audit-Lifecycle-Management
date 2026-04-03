package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.metadata.*;
import com.vulnaudit.entity.metadata.*;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.metadata.*;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/assets/{assetId}/metadata")
@RequiredArgsConstructor
@Tag(name = "资产元数据管理", description = "资产应用元数据CRUD接口 (按模块拆分)")
@SecurityRequirement(name = "bearerAuth")
public class AssetMetadataController {

    private final AssetMetadataBaseRepository baseRepository;
    private final AssetMetadataNetworkRepository networkRepository;
    private final AssetMetadataEnvRepository envRepository;
    private final AssetMetadataAccountRepository accountRepository;
    private final AssetMetadataExtraRepository extraRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        String username = authentication.getName();
        return userService.findByUsername(username)
                .map(user -> user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));
    }

    private UUID resolveAssetVersionId(UUID assetId, UUID assetVersionId) {
        if (assetVersionId != null) {
            boolean exists = assetVersionRepository.findByIdAndAssetId(assetVersionId, assetId).isPresent();
            if (!exists) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "资产版本不存在或不属于当前资产");
            }
            return assetVersionId;
        }
        return assetVersionRepository.findByAssetIdAndIsDefaultTrue(assetId)
                .map(version -> version.getId())
                .orElse(null);
    }

    // ================= BASE =================
    @GetMapping("/base")
    @Operation(summary = "获取基础元数据")
    public ResponseEntity<ApiResponse<AssetMetadataBase>> getBaseMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        Optional<AssetMetadataBase> data = resolvedVersionId != null 
                ? baseRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : baseRepository.findByAssetIdAndAssetVersionIdIsNull(assetId);
        return data.map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PutMapping("/base")
    @Operation(summary = "更新基础元数据")
    public ResponseEntity<ApiResponse<AssetMetadataBase>> updateBaseMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestBody AssetMetadataBaseRequest request) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        UUID currentUserId = getCurrentUserId();

        AssetMetadataBase metadata = (resolvedVersionId != null 
                ? baseRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : baseRepository.findByAssetIdAndAssetVersionIdIsNull(assetId))
                .orElse(new AssetMetadataBase());

        if (metadata.getId() == null) {
            metadata.setAssetId(assetId);
            metadata.setAssetVersionId(resolvedVersionId);
            metadata.setVersion(1);
        } else {
            metadata.setVersion(metadata.getVersion() + 1);
        }
        metadata.setClaimedBy(currentUserId);
        
        metadata.setSystemArchitecture(request.getSystemArchitecture());
        metadata.setRuntimePlatform(request.getRuntimePlatform());
        metadata.setDeploymentMethod(request.getDeploymentMethod());
        metadata.setProgrammingLanguagesJson(request.getProgrammingLanguagesJson());
        metadata.setProgrammingLanguage(request.getProgrammingLanguage());
        metadata.setFramework(request.getFramework());

        AssetMetadataBase saved = baseRepository.save(metadata);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ================= NETWORK =================
    @GetMapping("/network")
    @Operation(summary = "获取网络与接口元数据")
    public ResponseEntity<ApiResponse<AssetMetadataNetwork>> getNetworkMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        Optional<AssetMetadataNetwork> data = resolvedVersionId != null 
                ? networkRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : networkRepository.findByAssetIdAndAssetVersionIdIsNull(assetId);
        return data.map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PutMapping("/network")
    @Operation(summary = "更新网络与接口元数据")
    public ResponseEntity<ApiResponse<AssetMetadataNetwork>> updateNetworkMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestBody AssetMetadataNetworkRequest request) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        UUID currentUserId = getCurrentUserId();

        AssetMetadataNetwork metadata = (resolvedVersionId != null 
                ? networkRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : networkRepository.findByAssetIdAndAssetVersionIdIsNull(assetId))
                .orElse(new AssetMetadataNetwork());

        if (metadata.getId() == null) {
            metadata.setAssetId(assetId);
            metadata.setAssetVersionId(resolvedVersionId);
            metadata.setVersion(1);
        } else {
            metadata.setVersion(metadata.getVersion() + 1);
        }
        metadata.setClaimedBy(currentUserId);

        metadata.setServicePorts(request.getServicePorts());
        metadata.setApiEndpoints(request.getApiEndpoints());

        AssetMetadataNetwork saved = networkRepository.save(metadata);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ================= ENV =================
    @GetMapping("/env")
    @Operation(summary = "获取环境元数据")
    public ResponseEntity<ApiResponse<AssetMetadataEnv>> getEnvMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        Optional<AssetMetadataEnv> data = resolvedVersionId != null 
                ? envRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : envRepository.findByAssetIdAndAssetVersionIdIsNull(assetId);
        return data.map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PutMapping("/env")
    @Operation(summary = "更新环境元数据")
    public ResponseEntity<ApiResponse<AssetMetadataEnv>> updateEnvMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestBody AssetMetadataEnvRequest request) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        UUID currentUserId = getCurrentUserId();

        AssetMetadataEnv metadata = (resolvedVersionId != null 
                ? envRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : envRepository.findByAssetIdAndAssetVersionIdIsNull(assetId))
                .orElse(new AssetMetadataEnv());

        if (metadata.getId() == null) {
            metadata.setAssetId(assetId);
            metadata.setAssetVersionId(resolvedVersionId);
            metadata.setVersion(1);
        } else {
            metadata.setVersion(metadata.getVersion() + 1);
        }
        metadata.setClaimedBy(currentUserId);

        metadata.setInstallPath(request.getInstallPath());
        metadata.setStartupScriptPath(request.getStartupScriptPath());
        metadata.setConfigFilePath(request.getConfigFilePath());
        metadata.setStartupCommand(request.getStartupCommand());
        metadata.setDatabaseType(request.getDatabaseType());
        metadata.setDatabaseConnection(request.getDatabaseConnection());

        AssetMetadataEnv saved = envRepository.save(metadata);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ================= ACCOUNT =================
    @GetMapping("/account")
    @Operation(summary = "获取认证元数据")
    public ResponseEntity<ApiResponse<AssetMetadataAccount>> getAccountMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        Optional<AssetMetadataAccount> data = resolvedVersionId != null 
                ? accountRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : accountRepository.findByAssetIdAndAssetVersionIdIsNull(assetId);
        return data.map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PutMapping("/account")
    @Operation(summary = "更新认证元数据")
    public ResponseEntity<ApiResponse<AssetMetadataAccount>> updateAccountMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestBody AssetMetadataAccountRequest request) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        UUID currentUserId = getCurrentUserId();

        AssetMetadataAccount metadata = (resolvedVersionId != null 
                ? accountRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : accountRepository.findByAssetIdAndAssetVersionIdIsNull(assetId))
                .orElse(new AssetMetadataAccount());

        if (metadata.getId() == null) {
            metadata.setAssetId(assetId);
            metadata.setAssetVersionId(resolvedVersionId);
            metadata.setVersion(1);
        } else {
            metadata.setVersion(metadata.getVersion() + 1);
        }
        metadata.setClaimedBy(currentUserId);

        metadata.setAdminUsername(request.getAdminUsername());
        if (request.getAdminPasswordEncrypted() != null && !request.getAdminPasswordEncrypted().isBlank()) {
            metadata.setAdminPasswordEncrypted(passwordEncoder.encode(request.getAdminPasswordEncrypted()));
        }
        metadata.setAccountCredentialsJson(normalizeAccountCredentialsJson(request.getAccountCredentialsJson(), metadata.getAccountCredentialsJson()));

        AssetMetadataAccount saved = accountRepository.save(metadata);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ================= EXTRA =================
    @GetMapping("/extra")
    @Operation(summary = "获取附加元数据")
    public ResponseEntity<ApiResponse<AssetMetadataExtra>> getExtraMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        Optional<AssetMetadataExtra> data = resolvedVersionId != null 
                ? extraRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : extraRepository.findByAssetIdAndAssetVersionIdIsNull(assetId);
        return data.map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PutMapping("/extra")
    @Operation(summary = "更新附加元数据")
    public ResponseEntity<ApiResponse<AssetMetadataExtra>> updateExtraMetadata(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestBody AssetMetadataExtraRequest request) {
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        UUID currentUserId = getCurrentUserId();

        AssetMetadataExtra metadata = (resolvedVersionId != null 
                ? extraRepository.findByAssetIdAndAssetVersionId(assetId, resolvedVersionId)
                : extraRepository.findByAssetIdAndAssetVersionIdIsNull(assetId))
                .orElse(new AssetMetadataExtra());

        if (metadata.getId() == null) {
            metadata.setAssetId(assetId);
            metadata.setAssetVersionId(resolvedVersionId);
            metadata.setVersion(1);
        } else {
            metadata.setVersion(metadata.getVersion() + 1);
        }
        metadata.setClaimedBy(currentUserId);

        metadata.setSpecialNotes(request.getSpecialNotes());

        AssetMetadataExtra saved = extraRepository.save(metadata);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    private String normalizeAccountCredentialsJson(String rawJson, String existingJson) {
        if (rawJson == null) return null;
        if (rawJson.isBlank()) return "[]";
        try {
            ArrayNode input = (ArrayNode) objectMapper.readTree(rawJson);
            ArrayNode existing = existingJson != null && !existingJson.isBlank() 
                    ? (ArrayNode) objectMapper.readTree(existingJson) 
                    : objectMapper.createArrayNode();
            ArrayNode normalized = objectMapper.createArrayNode();

            for (int i = 0; i < input.size(); i++) {
                JsonNode node = input.get(i);
                if (!node.isObject()) continue;
                String usageNote = node.path("usageNote").asText(null);
                String username = node.path("username").asText(null);
                String password = node.path("password").asText(null);
                String existingEncrypted = node.path("passwordEncrypted").asText(null);

                if (existingEncrypted == null && existing.size() > i) {
                    existingEncrypted = existing.get(i).path("passwordEncrypted").asText(null);
                }

                String resolvedEncrypted = existingEncrypted;
                if (password != null && !password.isBlank()) {
                    resolvedEncrypted = passwordEncoder.encode(password);
                }

                if ((usageNote == null || usageNote.isBlank()) && 
                    (username == null || username.isBlank()) && 
                    (resolvedEncrypted == null || resolvedEncrypted.isBlank())) {
                    continue;
                }

                ObjectNode account = objectMapper.createObjectNode();
                if (usageNote != null && !usageNote.isBlank()) account.put("usageNote", usageNote.trim());
                if (username != null && !username.isBlank()) account.put("username", username.trim());
                if (resolvedEncrypted != null && !resolvedEncrypted.isBlank()) account.put("passwordEncrypted", resolvedEncrypted);
                normalized.add(account);
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账户信息格式错误");
        }
    }
}
