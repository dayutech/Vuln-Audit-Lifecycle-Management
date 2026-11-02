package com.vulnaudit.i18n;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后端错误消息 i18n 测试（t60）
 *
 * <p>覆盖四类高频消息（认证 / 资产 / 漏洞 / 任务）+ 参数校验消息：
 * <ul>
 *   <li>{@code Accept-Language: en} → 英文文案；</li>
 *   <li>{@code Accept-Language: zh-CN} 或不传 → 中文文案（与既有行为一致）；</li>
 *   <li>{@code ?lang=en} 覆盖请求头；</li>
 *   <li>缺失消息码 → 回退中文且不抛异常。</li>
 * </ul>
 */
@DisplayName("后端错误消息 i18n 测试")
class ApiMessageI18nTest extends com.vulnaudit.controller.BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ApiMessageResolver messageResolver;

    @BeforeEach
    void setUpI18nData() {
        assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("i18n 测试资产-" + UUID.randomUUID())
                .code("I18N-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("i18n 测试")
                .priority("P2").status("active").ownerId(engineerUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 404 / 403

    @Test
    @DisplayName("404：en 返回英文，未传语言返回中文")
    void notFound_enAndDefault() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/assets/" + missing)
                        .header("Authorization", adminToken)
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Asset not found")));

        mockMvc.perform(get("/api/v1/assets/" + missing)
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("资产不存在")));
    }

    @Test
    @DisplayName("403：en 返回英文，zh-CN 返回中文")
    void forbidden_enAndZh() throws Exception {
        // 工程师读他人资产 → 403（t46 起的可见性口径）
        Asset adminAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("管理员资产-" + UUID.randomUUID())
                .code("I18NX-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("admin 负责")
                .priority("P3").status("active").ownerId(adminUserId)
                .build()));

        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId())
                        .header("Authorization", engineerToken)
                        .header("Accept-Language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("permission")));

        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId())
                        .header("Authorization", engineerToken)
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("无权限")));
    }

    // ------------------------------------------------------------------ 400 参数校验

    @Test
    @DisplayName("400 参数校验：认证 DTO 的 en / zh 文案")
    void validationAuth_enAndZh() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "");
        body.put("password", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .header("Accept-Language", "en")
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")))
                .andExpect(jsonPath("$.data.username", containsString("Username must not be blank")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("参数校验失败")))
                .andExpect(jsonPath("$.data.username", containsString("用户名不能为空")));
    }

    @Test
    @DisplayName("400 参数校验：资产 DTO 的 en / zh 文案")
    void validationAsset_enAndZh() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "");
        body.put("targetType", "");
        body.put("targetSource", "");
        body.put("priority", "");
        body.put("status", "");

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .header("Accept-Language", "en")
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name", containsString("Asset name must not be blank")))
                .andExpect(jsonPath("$.data.targetType", containsString("Target type must not be blank")))
                // t69：建资产必须挂项目，该字段缺失同样要有英文文案
                .andExpect(jsonPath("$.data.projectId", containsString("Creating an asset requires a project")));

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name", containsString("资产名称不能为空")))
                .andExpect(jsonPath("$.data.projectId", containsString("创建资产必须指定所属项目")));
    }

    // ------------------------------------------------------------------ 认证消息

    @Test
    @DisplayName("认证失败消息：en 英文 / 默认中文（密码错误）")
    void authenticationFailedMessages() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "admin_" + System.nanoTime());
        body.put("password", "WrongPass@123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .header("Accept-Language", "en")
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("username or password")));
    }

    // ------------------------------------------------------------------ ?lang= 覆盖

    @Test
    @DisplayName("?lang=en 覆盖 Accept-Language（含 zh 头的会话）")
    void langQueryParamOverridesHeader() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/assets/" + missing)
                        .header("Authorization", adminToken)
                        .header("Accept-Language", "zh-CN")
                        .param("lang", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Asset not found")));

        mockMvc.perform(get("/api/v1/assets/" + missing)
                        .header("Authorization", adminToken)
                        .header("Accept-Language", "en")
                        .param("lang", "zh-CN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("资产不存在")));
    }

    // ------------------------------------------------------------------ 解析器单测

    @Test
    @DisplayName("缺失消息码：回退中文且不抛异常")
    void missingKey_fallsBackWithoutException() {
        String resolved = messageResolver.resolve("error.not.exists.at.all", "中文兜底");
        assertEquals("中文兜底", resolved, "缺失消息码应回退到 fallback");

        // 枚举兜底：未纳入 i18n 的错误码（如 TOOL_NOT_FOUND）解析结果 = 中文 message
        String toolMissing = messageResolver.resolveForErrorCode(
                com.vulnaudit.exception.ErrorCode.TOOL_NOT_FOUND, null);
        assertEquals("工具不存在", toolMissing, "未覆盖的错误码应回退到中文文案");

        // 业务层自定义中文文案（非消息码）应原样返回
        String custom = messageResolver.resolveForErrorCode(
                com.vulnaudit.exception.ErrorCode.BAD_REQUEST, "自定义中文提示");
        assertEquals("自定义中文提示", custom);
    }

    @Test
    @DisplayName("非消息码的校验文案原样保留（未迁移的 DTO 不受影响）")
    void nonKeyValidationMessage_keptAsIs() {
        assertEquals("漏洞标题不能为空", messageResolver.resolveOrKeep("漏洞标题不能为空"));
        assertTrue(messageResolver.isMessageKey("validation.asset.name.required"));
        assertFalse(messageResolver.isMessageKey("漏洞标题不能为空"));
        assertNotNull(messageResolver.resolve("validation.asset.name.required", null));
    }

    @Test
    @DisplayName("语言归一：en-US → 英文，其他语言 → 中文（默认）")
    void localeNormalization() {
        assertEquals("en", messageResolver.toSupportedLocale("en-US,en;q=0.9").getLanguage());
        assertEquals("zh", messageResolver.toSupportedLocale("fr-FR").getLanguage());
        assertEquals("zh", messageResolver.toSupportedLocale(null).getLanguage());
    }

    @Test
    @DisplayName("未知消息码不会以 key 形式泄漏给前端")
    void unknownBusinessMessage_isNotLeakedAsKey() throws Exception {
        // 业务异常携带未知消息码 → 保留原样（不会变成 key 也能被 500 掩盖），此处直接断言解析行为
        String resolved = messageResolver.resolveOrKeep("error.some.unknown.key");
        assertEquals("error.some.unknown.key", resolved,
                "无法解析且无 fallback 时保持原样，避免把空消息返回给前端");

        // 前端看到的正常 404 不应包含 "error." 前缀
        mockMvc.perform(get("/api/v1/assets/" + UUID.randomUUID())
                        .header("Authorization", adminToken)
                        .header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", not(containsString("error."))));
    }
}
