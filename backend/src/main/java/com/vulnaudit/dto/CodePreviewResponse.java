package com.vulnaudit.dto;

/**
 * 编码预览响应（t192）。
 *
 * <p>新建表单在打开时就向后端要一个「按规则已生成、且当前未被占用」的编码并只读展示，
 * 用户无需（也不能）手动填写；提交时把同一编码带回，服务端再做一次唯一性校验。
 *
 * @param code 预览编码（客户 CUST-XXXXXXX / 项目 PRJ-yyyy-MM-dd-XXXXXX / 资产 AST-yyyyMMdd-XXXXXX）
 */
public record CodePreviewResponse(String code) {
}