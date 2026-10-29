package com.vulnaudit.service;

import com.vulnaudit.dto.ImportResult;
import com.vulnaudit.dto.ImportUsersResult;

import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * 批量导入服务
 *
 * <p>支持通过 CSV（UTF-8，首行表头）批量导入资产、漏洞与用户，逐行校验、部分成功。
 *
 * <p>实现约束：
 * <ul>
 *     <li>每一行都调用既有的 {@code AssetService/VulnerabilityService/UserService/RoleService}
 *         完成落库，复用其枚举校验、唯一性校验、密码编码与角色绑定规则，不绕过服务层；</li>
 *     <li>单行失败不影响其他行（逐行独立事务）；</li>
 *     <li>导入结束后写审计日志。</li>
 * </ul>
 */
public interface ImportService {

    /**
     * 导入资产
     *
     * <p><b>注意</b>：本重载不做团队范围限制，仅供内部/测试使用；
     * HTTP 入口必须走带 {@code allowedTeamIds} 的重载（fail-closed）。
     *
     * @param content    CSV 文件内容（UTF-8）
     * @param fileName   原始文件名（仅用于审计与日志）
     * @param operatorId 操作者ID
     * @return 导入结果（成功行数 + 失败明细）
     */
    ImportResult importAssets(byte[] content, @Nullable String fileName, @Nullable UUID operatorId);

    /**
     * 导入资产（带团队范围）
     *
     * <p>数据边界：{@code allowedTeamIds} 为 {@code null} 表示不限制（admin / project_leader）；
     * 非 null 时每一行的 {@code teamId}/{@code teamCode} 必须落在集合内，否则该行记入
     * {@code failedRows}（原因以「权限拒绝：」开头）且不落库。
     *
     * @param content        CSV 文件内容（UTF-8）
     * @param fileName       原始文件名
     * @param operatorId     操作者ID
     * @param allowedTeamIds 允许导入的团队ID集合；{@code null} 表示不限制
     * @return 导入结果
     */
    ImportResult importAssets(byte[] content, @Nullable String fileName, @Nullable UUID operatorId,
                              @Nullable java.util.Set<UUID> allowedTeamIds);

    /**
     * 导入漏洞
     *
     * <p><b>注意</b>：本重载不做逐行资产授权，仅供内部/测试使用；
     * HTTP 入口必须走带 {@code assetWritable} 的重载（fail-closed）。
     *
     * @param content    CSV 文件内容（UTF-8）
     * @param fileName   原始文件名（仅用于审计与日志）
     * @param operatorId 操作者ID
     * @return 导入结果（成功行数 + 失败明细）
     */
    ImportResult importVulnerabilities(byte[] content, @Nullable String fileName, @Nullable UUID operatorId);

    /**
     * 导入漏洞（逐行按目标资产做写权限校验）
     *
     * <p>数据边界：{@code assetWritable} 为 {@code null} 表示不限制（内部调用）；
     * 非 null 时每一行解析出的目标资产必须通过该判定，否则该行记入 {@code failedRows}
     * （原因以「权限拒绝：」开头）且不落库（包括不会为被拒资产创建版本）。
     *
     * @param content       CSV 文件内容（UTF-8）
     * @param fileName      原始文件名
     * @param operatorId    操作者ID
     * @param assetWritable 资产可写判定；{@code null} 表示不限制
     * @return 导入结果
     */
    ImportResult importVulnerabilities(byte[] content, @Nullable String fileName, @Nullable UUID operatorId,
                                       @Nullable java.util.function.Predicate<UUID> assetWritable);

    /**
     * 导入用户（SRS 4.5.3）
     *
     * <p>CSV 不含密码列：每行生成随机强初始密码，经 {@code UserService.createUser} 完成
     * BCrypt 编码与用户名/邮箱唯一性校验，再经 {@code RoleService.assignRoleToUser} 绑定角色
     * （分配人记为 operatorId）。初始密码随响应返回一次，由管理员分发并要求首次登录后修改
     * （users 表无 must_change_password 列，故以响应标记 + 管理员重置密码流程约束）。
     *
     * @param content    CSV 文件内容（UTF-8）
     * @param fileName   原始文件名（仅用于审计与日志）
     * @param operatorId 操作者ID（同时作为角色分配人）
     * @return 导入结果（成功行数 + 失败明细 + 新建用户与初始密码）
     */
    ImportUsersResult importUsers(byte[] content, @Nullable String fileName, @Nullable UUID operatorId);

    /**
     * 资产导入模板
     */
    String assetTemplate();

    /**
     * 漏洞导入模板
     */
    String vulnerabilityTemplate();

    /**
     * 用户导入模板
     */
    String userTemplate();
}
