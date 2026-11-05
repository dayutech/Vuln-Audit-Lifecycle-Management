package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.SsoProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSO 提供商 Web 配置与「启用即展示」测试（t116）
 *
 * <p>覆盖：
 * <ul>
 *   <li>公开列表改为 enabled 即返回，并带 {@code configured} 标记（未配置不再被过滤掉）；</li>
 *   <li>管理端 GET 返回全部字段、secret 只回掩码；</li>
 *   <li>PUT 部分更新只改传入字段；secret 省略/空串保持不变、{@code clearClientSecret=true} 才清空；</li>
 *   <li>URL 必须 https（本地 localhost/127.0.0.1 放宽）；scopes 去重；未知 code 404；非 ADMIN 403；</li>
 *   <li>保存后立即生效（公开列表立刻反映 configured）；审计只记字段名且 secret 记 {@code ***}；</li>
 *   <li>updatedBy/updatedAt 落库并在管理端回显。</li>
 * </ul>
 */
@DisplayName("SSO 提供商 Web 配置测试")
public class SsoProviderConfigTest extends BaseTest {

    private static final String PUBLIC_PROVIDERS = "/api/v1/auth/sso/providers";
    private static final String ADMIN_PROVIDERS = "/api/v1/admin/sso/providers";
    private static final String SECRET = "super-secret-value-1234";

    @Autowired
    private SsoProviderRepository ssoProviderRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void resetProviders() {
        // 每个用例从「4 家默认：关闭、无凭据、端点预填」开始
        for (SsoProvider provider : ssoProviderRepository.findAll()) {
            provider.setEnabled(false);
            provider.setClientId(null);
            provider.setClientSecret(null);
            provider.setUpdatedBy(null);
            ssoProviderRepository.save(provider);
        }
    }

    private SsoProvider provider(String code) {
        return ssoProviderRepository.findByCode(code).orElseThrow();
    }

    private void enable(String code) {
        SsoProvider provider = provider(code);
        provider.setEnabled(true);
        ssoProviderRepository.save(provider);
    }

    private String adminUsername() {
        return userRepository.findById(adminUserId).orElseThrow().getUsername();
    }

    // ------------------------------------------------------------------ 公开列表

    @Test
    @DisplayName("公开列表：enabled 即返回，未配置项 configured=false（不再按配置过滤）")
    void publicListShowsEnabledUnconfigured() throws Exception {
        enable("github");

        mockMvc.perform(get(PUBLIC_PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("github"))
                .andExpect(jsonPath("$.data[0].configured").value(false));
    }

    @Test
    @DisplayName("公开列表：enabled=false 不返回")
    void disabledProviderNotInPublicList() throws Exception {
        mockMvc.perform(get(PUBLIC_PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ------------------------------------------------------------------ 部分更新

    @Test
    @DisplayName("PUT 部分更新：只改传入字段，其余保持不变")
    void putUpdatesOnlyGivenFields() throws Exception {
        String defaultAuthorizeUrl = provider("github").getAuthorizeUrl();

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"clientId\":\"cid-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value("cid-1"))
                .andExpect(jsonPath("$.data.clientSecretMasked").value(nullValue()))
                .andExpect(jsonPath("$.data.authorizeUrl").value(defaultAuthorizeUrl))
                .andExpect(jsonPath("$.data.enabled").value(false));

        SsoProvider saved = provider("github");
        assertEquals("cid-1", saved.getClientId());
        assertNull(saved.getClientSecret(), "未传 secret 不应写入");
        assertEquals(defaultAuthorizeUrl, saved.getAuthorizeUrl(), "未传的 URL 不应变化");
    }

    @Test
    @DisplayName("secret：空字符串不清空；clearClientSecret=true 才清空")
    void secretBlankKeepsExisting() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"clientSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"clientId\":\"cid-2\",\"clientSecret\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value("cid-2"));

        assertEquals(SECRET, provider("github").getClientSecret(), "空字符串不得清空 secret");

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"clearClientSecret\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientSecretMasked").value(nullValue()))
                .andExpect(jsonPath("$.data.configured").value(false));

        assertNull(provider("github").getClientSecret(), "clearClientSecret=true 应清空");
    }

    @Test
    @DisplayName("响应与日志不含明文 secret：管理端只回掩码，末尾 4 位可见")
    void secretNeverReturnedPlaintext() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientId\":\"cid\",\"clientSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientSecretMasked", containsString("****")))
                .andExpect(jsonPath("$.data.clientSecretMasked", containsString("1234")));

        String listBody = mockMvc.perform(get(ADMIN_PROVIDERS).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listBody.contains(SECRET), "管理端列表不得返回明文 secret: " + listBody);
        assertTrue(listBody.contains("****"), "应返回掩码形式");
    }

    // ------------------------------------------------------------------ 校验

    @Test
    @DisplayName("URL 校验：非 https（且非本地）→ 400；http://127.0.0.1 允许")
    void invalidUrlRejected() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"authorizeUrl\":\"http://evil.example.com/oauth/authorize\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("https")));

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tokenUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tokenUrl\":\"http://127.0.0.1:9443/token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenUrl").value("http://127.0.0.1:9443/token"));
    }

    @Test
    @DisplayName("未知 code → 404；非 ADMIN → 403；匿名 → 401")
    void unknownCodeAndPermissions() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/gitlab").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("不支持的登录方式")));

        mockMvc.perform(get(ADMIN_PROVIDERS).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_PROVIDERS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("scopes 逗号分隔去重（保持首次出现顺序）；空串清空")
    void scopesNormalized() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"scopes\":\" read:user , user:email ,, read:user \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopes").value("read:user, user:email"));

        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"scopes\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopes").value(nullValue()));
    }

    // ------------------------------------------------------------------ configured 判定与即时生效

    @Test
    @DisplayName("configured 需 5 个必填项齐全；保存后公开列表立即反映（无需重启）")
    void configuredRequiresAllFieldsAndTakesEffectImmediately() throws Exception {
        // 先清空三个端点，模拟全新配置
        mockMvc.perform(put(ADMIN_PROVIDERS + "/google").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"authorizeUrl\":\"\",\"tokenUrl\":\"\",\"userinfoUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));

        mockMvc.perform(put(ADMIN_PROVIDERS + "/google").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":true,\"clientId\":\"gid\",\"clientSecret\":\"gsecret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));

        mockMvc.perform(get(PUBLIC_PROVIDERS))
                .andExpect(jsonPath("$.data[0].code").value("google"))
                .andExpect(jsonPath("$.data[0].configured").value(false));

        mockMvc.perform(put(ADMIN_PROVIDERS + "/google").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"authorizeUrl\":\"https://accounts.google.com/o/oauth2/v2/auth\","
                                + "\"tokenUrl\":\"https://oauth2.googleapis.com/token\","
                                + "\"userinfoUrl\":\"https://openidconnect.googleapis.com/v1/userinfo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true));

        // 保存即生效：公开列表马上 configured=true
        mockMvc.perform(get(PUBLIC_PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("google"))
                .andExpect(jsonPath("$.data[0].configured").value(true));
    }

    @Test
    @DisplayName("updatedBy / updatedAt：保存后落库并在管理端回显")
    void updatedByRecorded() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/wechat").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedBy").value(adminUsername()))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertEquals(adminUserId, provider("wechat").getUpdatedBy());
    }

    // ------------------------------------------------------------------ 审计

    @Test
    @DisplayName("审计：UPDATE_SSO_PROVIDER 记变更字段名，secret 只记 ***，不含明文")
    void auditRecordsFieldNamesWithoutSecret() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientId\":\"cid\",\"clientSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_SSO_PROVIDER");
        assertEquals(1, logs.size(), "一次保存应恰好 1 条审计");
        String details = logs.get(0).getDetails();
        assertTrue(details.contains("provider=github"));
        assertTrue(details.contains("clientId"), "应记录变更字段名: " + details);
        assertTrue(details.contains("clientSecret=***"), "secret 只能记 ***: " + details);
        assertFalse(details.contains(SECRET), "审计不得出现明文 secret: " + details);

        List<AuditLog> interceptorRows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAction() != null && row.getAction().contains("AdminSsoController"))
                .toList();
        assertTrue(interceptorRows.isEmpty(),
                "该端点不应再由拦截器写重复审计，实际 " + interceptorRows.size() + " 条");
    }

    @Test
    @DisplayName("PATCH（t104 兼容）仍可只开关，一次只产生 1 条审计")
    void patchToggleStillWorks() throws Exception {
        mockMvc.perform(patch(ADMIN_PROVIDERS + "/github").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.configured").value(false));

        assertTrue(provider("github").isEnabled());
        assertEquals(1, auditLogRepository.findByAction("UPDATE_SSO_PROVIDER").size());
    }

    @Test
    @DisplayName("管理端列表：4 家齐全、按 code 排序、不含明文 secret")
    void adminListContainsAllProviders() throws Exception {
        mockMvc.perform(put(ADMIN_PROVIDERS + "/microsoft").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"clientSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_PROVIDERS).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].code").value("github"))
                .andExpect(jsonPath("$.data[1].code").value("google"))
                .andExpect(jsonPath("$.data[2].code").value("microsoft"))
                .andExpect(jsonPath("$.data[3].code").value("wechat"));
    }
}
