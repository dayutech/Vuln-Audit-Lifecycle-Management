package com.vulnaudit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 未知路径的 HTTP 语义测试（t188）。
 *
 * <p>缺陷现场：Spring 6.2 起，未匹配 Controller 的请求会落到静态资源处理器并抛
 * {@code NoResourceFoundException}；它不继承 {@code NoHandlerFoundException}，
 * 于是被 {@code @ExceptionHandler(Exception.class)} 兜成 **500 + ERROR 堆栈**。
 * 线上实测：`GET /api/v1/forms/templates`（拼错的路径）返回 500。
 *
 * <p>修复后：任意不存在的 API 路径都必须是 404，且响应体仍是统一结构（`success=false`）。
 */
@DisplayName("未知 API 路径返回 404（而非 500）")
class UnknownApiPathTest extends BaseTest {

    @Test
    @DisplayName("GET 不存在的 API 路径 → 404")
    void unknownGetPathReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/definitely-not-a-real-endpoint")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST 不存在的 API 路径 → 404")
    void unknownPostPathReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/forms/templates")
                        .header("Authorization", adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("未认证访问不存在的路径 → 401（鉴权先行，不泄漏路由是否存在）")
    void unauthenticatedUnknownPathReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/definitely-not-a-real-endpoint"))
                .andExpect(status().isUnauthorized());
    }
}