package com.vulnaudit.dto;

/**
 * 客户详情视图（t68）：客户本体 + 聚合计数
 *
 * @param customer           客户本体
 * @param projectCount       项目总数
 * @param activeProjectCount 进行中（ACTIVE）项目数
 * @param assetCount         该客户下资产挂靠的去重资产数
 */
public record CustomerDetailResponse(
        CustomerResponse customer,
        long projectCount,
        long activeProjectCount,
        long assetCount) {
}
