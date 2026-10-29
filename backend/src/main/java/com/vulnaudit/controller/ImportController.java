package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.ImportResult;
import com.vulnaudit.dto.ImportUsersResult;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.AccessGuard;
import com.vulnaudit.service.ImportService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

/**
 * 批量导入控制器
 *
 * <p>SRS 4.5.2：支持 Excel/CSV 批量导入资产、漏洞。
 * 本实现接受 UTF-8 编码、首行为表头的 CSV 文件，逐行校验并允许部分成功。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@Tag(name = "批量导入", description = "资产/漏洞 CSV 批量导入与模板下载")
@SecurityRequirement(name = "bearerAuth")
public class ImportController {

    /**
     * 上传文件大小上限（字节）：导入文件不应超过 20MB
     */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * UTF-8 BOM，保证 Excel 打开模板时中文不乱码
     */
    private static final String UTF8_BOM = "\uFEFF";

    private final ImportService importService;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 批量导入资产
     *
     * <p><b>写侧数据边界</b>：导入是批量写操作，此前任何登录用户都能灌入资产。
     * 现按角色分级：admin / project_leader 可导入任意团队；team_leader 只能导入
     * 自己领导或所属的团队（逐行校验，越权行记入 failedRows 且不落库）。
     */
    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "批量导入资产",
            description = "上传 UTF-8 CSV，首行表头：name,targetType,targetSource,priority,status,teamCode/teamId,description；"
                    + "仅 admin/project_leader/team_leader 可用，team_leader 只能导入自己领导或所属的团队")
    public ResponseEntity<ApiResponse<ImportResult>> importAssets(
            @Parameter(description = "CSV 文件") @RequestParam("file") MultipartFile file) {

        log.info("批量导入资产: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        UUID operatorId = getCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }

        Set<UUID> allowedTeamIds = resolveImportTeamScope(SecurityContextHolder.getContext().getAuthentication(),
                operatorId);
        ImportResult result = importService.importAssets(readBytes(file), file.getOriginalFilename(),
                operatorId, allowedTeamIds);
        return ResponseEntity.ok(ApiResponse.success(result, buildMessage(result)));
    }

    /**
     * 批量导入漏洞
     *
     * <p><b>写侧数据边界</b>：逐行按目标资产判定 {@code accessGuard.canWriteAsset}（与单条创建同一口径），
     * 无权行记入 failedRows（原因「权限拒绝：无权在该资产下创建漏洞」）并跳过，不影响其他行；
     * 判定异常按拒绝处理（fail-closed）。
     */
    @PostMapping(value = "/vulnerabilities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量导入漏洞",
            description = "上传 UTF-8 CSV，首行表头：title,assetCode,type,severity,cvssScore,workflowStatus,description；"
                    + "逐行校验目标资产可写权限，无权行记入 failedRows")
    public ResponseEntity<ApiResponse<ImportResult>> importVulnerabilities(
            @Parameter(description = "CSV 文件") @RequestParam("file") MultipartFile file) {

        log.info("批量导入漏洞: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        UUID operatorId = getCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // fail-closed：审批流里任何异常都由 AccessGuard 侧返回 false，这里不做兜底放行
        java.util.function.Predicate<UUID> assetWritable = assetId -> accessGuard.canWriteAsset(assetId, authentication);

        ImportResult result = importService.importVulnerabilities(readBytes(file), file.getOriginalFilename(),
                operatorId, assetWritable);
        return ResponseEntity.ok(ApiResponse.success(result, buildMessage(result)));
    }

    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "批量导入用户", description = "管理员上传 UTF-8 CSV，首行表头：username,email,fullName,phone,roleCode；"
            + "CSV 不含密码列，系统为每行生成 16 位强随机初始密码并在响应中返回，由管理员分发并建议首次登录后修改")
    public ResponseEntity<ApiResponse<ImportUsersResult>> importUsers(
            @Parameter(description = "CSV 文件") @RequestParam("file") MultipartFile file) {

        log.info("批量导入用户: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        UUID operatorId = getCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }

        ImportUsersResult result = importService.importUsers(readBytes(file), file.getOriginalFilename(), operatorId);
        return ResponseEntity.ok(ApiResponse.success(result, buildUserMessage(result)));
    }

    @GetMapping(value = "/templates/{type}")
    @Operation(summary = "下载导入模板", description = "type 取值 assets / vulnerabilities / users")
    public ResponseEntity<byte[]> downloadTemplate(@Parameter(description = "模板类型：assets / vulnerabilities / users") @PathVariable String type) {
        log.info("下载导入模板: type={}", type);

        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String content;
        String fileName;
        switch (normalized) {
            case "assets", "asset" -> {
                content = importService.assetTemplate();
                fileName = "assets_template.csv";
            }
            case "vulnerabilities", "vulnerability", "vulns", "vuln" -> {
                content = importService.vulnerabilityTemplate();
                fileName = "vulnerabilities_template.csv";
            }
            case "users", "user" -> {
                content = importService.userTemplate();
                fileName = "users_template.csv";
            }
            default -> throw new BusinessException(ErrorCode.NOT_FOUND,
                    "不支持的模板类型: " + type + "（可选 assets / vulnerabilities / users）");
        }

        byte[] body = (UTF8_BOM + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .body(body);
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件过大，单次导入最大 20MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取上传文件失败: " + e.getMessage());
        }
    }

    private String buildMessage(ImportResult result) {
        if (result.totalRows() == 0) {
            return "文件没有数据行";
        }
        if (result.failedRows().isEmpty()) {
            return "导入成功 " + result.successRows() + " 行";
        }
        return "导入完成：成功 " + result.successRows() + " 行，失败 " + result.failedRows().size() + " 行";
    }

    private String buildUserMessage(ImportUsersResult result) {
        if (result.totalRows() == 0) {
            return "文件没有数据行";
        }
        String base = result.failedRows().isEmpty()
                ? "导入成功 " + result.successRows() + " 行"
                : "导入完成：成功 " + result.successRows() + " 行，失败 " + result.failedRows().size() + " 行";
        if (result.successRows() > 0) {
            base += "；初始密码仅在本次响应返回，请管理员分发并要求首次登录后修改";
        }
        return base;
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    /**
     * 资产导入的团队范围（fail-closed）
     *
     * <ul>
     *   <li>admin / project_leader → 返回 {@code null}（不限制）</li>
     *   <li>team_leader → 返回「自己领导的团队 ∪ 自己所属的团队」的 ID 集合</li>
     *   <li>其它情况（认证为空 / 解析不到用户 / 无相关角色）→ 返回空集合，一律拒绝，绝不放行</li>
     * </ul>
     *
     * @param authentication 当前认证
     * @param operatorId     当前用户ID
     * @return 允许导入的团队ID集合；{@code null} 表示不限制
     */
    private Set<UUID> resolveImportTeamScope(@org.springframework.lang.Nullable Authentication authentication,
                                             @org.springframework.lang.Nullable UUID operatorId) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        if (authorities.contains(RoleAuthority.ADMIN) || authorities.contains(RoleAuthority.PROJECT_LEADER)) {
            return null;
        }
        if (operatorId == null) {
            return Set.of();
        }
        Set<UUID> teamIds = new LinkedHashSet<>();
        teamRepository.findByLeaderId(operatorId).forEach(team -> teamIds.add(team.getId()));
        teamIds.addAll(teamMemberRepository.findTeamIdsByUserId(operatorId));
        return teamIds;
    }
}
