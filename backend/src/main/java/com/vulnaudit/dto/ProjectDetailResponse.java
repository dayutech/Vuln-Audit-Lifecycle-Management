package com.vulnaudit.dto;

import java.util.List;

/**
 * 项目详情视图（t68）
 *
 * @param project   项目本体（含客户名与经理名）
 * @param customer  客户摘要
 * @param phases    期次列表（按 phaseNo 升序）
 * @param assetCount 该项目下资产挂靠的去重资产数
 */
public record ProjectDetailResponse(
        ProjectResponse project,
        CustomerBrief customer,
        List<PhaseResponse> phases,
        long assetCount) {
}
