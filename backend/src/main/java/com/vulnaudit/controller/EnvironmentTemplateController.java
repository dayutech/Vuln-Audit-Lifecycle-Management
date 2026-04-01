package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.entity.EnvironmentTemplate;
import com.vulnaudit.repository.EnvironmentTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/environment-templates")
@RequiredArgsConstructor
@Tag(name = "环境模板管理", description = "环境模板管理接口")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentTemplateController {

    private final EnvironmentTemplateRepository environmentTemplateRepository;

    @GetMapping
    @Operation(summary = "获取环境模板列表", description = "获取所有环境模板")
    public ResponseEntity<ApiResponse<List<EnvironmentTemplate>>> listTemplates() {
        log.info("获取环境模板列表");
        List<EnvironmentTemplate> templates = environmentTemplateRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }
}
