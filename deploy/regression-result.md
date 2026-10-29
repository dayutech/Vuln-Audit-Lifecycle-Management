# 验收级后端全量回归结果（t25）

> 本文件是 t25 的验收产出：**全部后端任务合并后**的全量回归证据。
> 与 t12 的区别：t12 是预跑（当时 t20/t21/t22/t23 未落地，410 例），本文件是合并后的正式口径。
> 采集时间：2026-10-29 22:46 ~ 23:20（Asia/Shanghai）。

## 1. 快照与 git 状态

| 项 | 值 |
|---|---|
| git HEAD | `1785ea7416ff642b41251a5056b7b63b0a12ec84`（`docs(deploy): 冒烟复跑与最终状态补充`） |
| 工作区 | `git status --porcelain` 为空（WORKTREE_CLEAN），无未提交改动 |
| 快照范围 | `backend/src` + `backend/pom.xml` + `backend/lombok.config`，共 **334** 个文件 |
| manifest | `.tmp-t25/manifest.txt`（逐文件 `sha256  path`）；**manifest sha256 = `77535ad21d0b1cdda6d5f74cb7ae1cbbaf7c546fe87356c244d59b7218f5fc4c`** |
| 一致性 | 跑前、跑完第一轮、跑完第二轮三次计算的 manifest 摘要**完全相同** → 两次回归跑在同一份源码上 |
| 与线上产物的关系 | t13 部署的 jar 由同一提交构建（本地/远端 `pom.xml`、`PermissionCatalog.java` sha256 前 16 位一致） |

## 2. 两次全量回归结果

命令：`cd backend && mvn -B clean test`（JDK 17，独占 `backend/target`，期间无其他成员构建）

| 轮次 | 开始 | 耗时 | 结果 |
|---|---|---|---|
| 第 1 次 | 2026-10-29 22:46 | **16:17 min** | Tests run: **452**, Failures: **0**, Errors: **0**, Skipped: **0** → BUILD SUCCESS |
| 第 2 次 | 2026-10-29 23:02 | **16:42 min** | Tests run: **452**, Failures: **0**, Errors: **0**, Skipped: **0** → BUILD SUCCESS |

- surefire 汇总（第 2 次）：**54 个 suite / 452 用例 / 0 失败 / 0 错误 / 0 跳过**，通过率 **100%**。
- 失败清单：**无**。
- 跳过/忽略：`grep -rn "@Disabled\|@Ignore" backend/src` → **0 命中**，本轮没有用禁用绕过任何用例。
- 相对 t12 预跑的增量：410 → **452 例（+42）**，全部来自 t21/t22/t23/t27/t29/t31/t32/t33/t34/t37 新增的授权、并发与 DTO 用例。

## 3. 静态检查

| 检查项 | 命令 | 结果 |
|---|---|---|
| `createdBy(null)` 残留 | `grep -rn "createdBy(null)" backend/src/main/java` | **0 命中**（t23 已用 `CurrentUserProvider` 统一写入） |
| 禁用用例 | `grep -rn "@Disabled\|@Ignore" backend/src` | **0 命中** |
| Flyway 版本号唯一且无缺号 | `ls backend/src/main/resources/db/migration/V*.sql \| sort -V` | **V1…V17 连续 17 个，无重号无缺号**（任务描述写的“当前应到 V16”，实际已到 **V17**，V17 为 t32 的文档版本唯一索引） |

## 4. 空库引导验证（真实建库路径）

口径：生产用 `SPRING_FLYWAY_ENABLED=false` + `ddl-auto=update`，因此验证这条路径而不是 Flyway 串联。
执行脚本：`.tmp-t25/tmpdb-bootstrap.sh`（远端 `/tmp/tmpdb-bootstrap.sh`），**不触碰生产库 `vuln_audit`**。

| 步骤 | 结果 |
|---|---|
| 新建 `vuln_audit_tmp`（utf8mb4）并授权 `vulnapp` | PASS |
| 以 `SERVER_PORT=8081`、`DB_NAME=vuln_audit_tmp`、Flyway 关闭、`ddl-auto=update` 启动 jar | PASS（约 12s 启动，`/v3/api-docs`=200） |
| 建表数量 | **56 张**（= 50 个单行 `@Table` 实体 + 5 张 `entity/metadata/*` + `asset_versions`，与实体定义一一对应） |
| RBAC 启动初始化 | PASS：`新增权限点=90, 新增角色=4, 新增角色权限=261, 权限点总数=90`；DB `permissions=90 / role_permissions=261` |
| 播种 admin（空库无用户；从生产库按显式列名复制 `password_hash` 并绑定 admin 角色） | PASS |
| admin 登录 | PASS（200，token 202 字符） |
| `GET /users/me/permissions` | PASS：**permissionCount=90**；`/permissions` 返回 **90** 个权限点 |
| 健康检查 | PASS：`/v3/api-docs`=**200** + `POST /auth/login`=**200**（本项目无 actuator，见 §6） |
| 生产库/生产服务未受影响 | PASS：`vuln_audit` tables=56、assets=3；`127.0.0.1:8088` 登录仍 200 |
| 收尾 | 停止临时实例、`DROP DATABASE vuln_audit_tmp`，复查 schemata 中已无该库 → PASS |

## 5. 重点新增用例覆盖核对

以下类全部在 452 例之内且全绿（数字为 surefire 实测用例数）：

| 需求 | 用例类 | 例数 | 覆盖点 |
|---|---|---|---|
| t21 漏洞子资源越权 | `VulnerabilityAuthorizationTest` | 10 | 他人资产漏洞的详情/标签/历史/附件/按资产列表 → **403**；负责人 200；写侧 403 |
| t22 passwordHash | `UserPasswordIntegrityTest` | 5 | 各用户相关响应 JSON **不含 passwordHash**；改密后哈希仍非空；PATCH→PUT→DELETE 不触发 NOT NULL |
| t23 工具版本 createdBy/文件 | `ToolVersionConcurrencyTest` | 2 | 版本上传 `createdBy` 非空、`hasFile=true`、下载字节一致、并发 versionNo 唯一（409 语义在单测覆盖） |
| t27 创建漏洞越权 + 统计收敛 | `VulnerabilityDtoBindingTest` / `DashboardStatsAuthorizationTest` | 9 / 6 | 他人资产创建漏洞 **403 且漏洞数不变**；统计端点工程师 403、leader 只统计本团队可见资产 |
| t29/t31 仪表盘授权 | `DashboardStatsAuthorizationTest` / `DashboardExportAuthorizationTest` | 6 / 6 | 统计与导出角色门槛；leader 数值收敛；导出 BOM+表头+含数据 |
| t32/t37 文档版本 | `DocumentVersionConcurrencyTest` / `DocumentRollbackSnapshotTest` | 3 / 4 | 并发更新无 5xx、版本连续；回滚写新快照、changelog `rollback from vN`、历史不可变 |
| t33 导入写侧边界 | `ImportAuthorizationTest` | 8 | 工程师导入资产 403；团队长跨团队行 failed 不落库；漏洞导入按 canWriteAsset 逐行拒绝 |
| t4/t18 导入与用户导入 | `ImportServiceImplTest` / `ImportControllerTest` / `ImportUsersTest` | 13 / 5 / 7 | 部分成功语义、CSV 解析、用户导入仅 ADMIN |
| t16 通知 type | `NotificationTypeLengthTest` | 5 | 不传 200 / 50 字符 200 / 51 字符 400 |
| t1/t5/t2 基础能力 | `RbacPermissionTest` / `EnvironmentServiceImplTest` / `AuditLogServiceQueryTest` / `AuditLogEnhancementTest` / `PermissionBootstrapRepairTest` | 22 / 12 / 6 / 7 / 5 | 权限点 90 与幂等自愈、部署器 503 语义、审计筛选/导出/清理、RBAC 破坏后自愈 |
| t12 新增薄弱分支用例 | `AuditLogServiceQueryTest`(6)、`PermissionBootstrapRepairTest`(5)、`EnvironmentProvisionerSelectorTest`(4)、`DashboardTrendServiceTest`(4) | **19** | 审计 CSV 转义与清理回落 180、RBAC 自愈、部署器切换与不缓存、workload 趋势 coalesce 与排行归一 |

> 上表 20 个类合计 **146 例**，均包含在 452 例总数内。

## 6. 已知偏离（不构成失败，需写入验收报告）

1. **Flyway「空库 V1→最新串联」不作为验收条件**：生产关闭 Flyway（`SPRING_FLYWAY_ENABLED=false`），改由 Hibernate `ddl-auto=update` 建表；迁移脚本里只有 7 条 `CREATE TABLE`，核心表由实体创建，`V2/V3/V4` 直接 `ALTER` 实体表，空库串联必然失败（`deploy/README.md` §6 已记录）。验收口径因此改为「版本号唯一且无缺号」，实测 V1…V17 通过；真实建库路径已在 §4 验证。
2. **项目未引入 `spring-boot-starter-actuator`**：`/actuator/health` 返回 500（`NoResourceFoundException`）。健康检查口径改为 `/v3/api-docs` 200 + `POST /auth/login` 200，本轮回归与 §4 空库引导均按此执行。
3. **工具版本并发 409 由单测覆盖**：`t28` 的行级锁把版本号分配串行化，线上 3 并发实测 201/201/201 且 versionNo 唯一（无 500），409 分支由 `ToolVersionConcurrencyTest` 覆盖。
4. **`DELETE /assets/{id}` 是软删除**（置 `archived`），属设计选择，非缺陷。

## 7. 结论

- ✅ 同一份源码快照（manifest `77535ad2…`，git `1785ea7` 干净工作区）连续两次 `mvn -B clean test` 全绿：**452/452，0 失败 0 错误 0 跳过**。
- ✅ 空库引导验证通过：**56 张表 / RBAC 90 权限点 261 关联 / admin 可登录 / permissionCount=90 / api-docs+登录 200**，生产库未受影响，临时库已删除。
- ✅ 静态检查通过：无 `createdBy(null)` 残留、无 `@Disabled/@Ignore`、Flyway 版本号唯一无缺号（V1…V17）。
- ✅ 重点新增用例（t21/t22/t23/t27/t29/t31/t32/t33/t34/t37 + t12 的 19 例）全部在册且全绿。

**t25 判定：通过。** 后续 t14 可直接引用本文件与 `deploy/smoke-result.txt` 作为验收证据。
