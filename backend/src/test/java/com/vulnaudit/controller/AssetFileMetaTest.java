package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetFile;
import com.vulnaudit.entity.AssetDeliverable;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.repository.AssetDeliverableRepository;
import com.vulnaudit.repository.AssetFileRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资产文件 / 交付物的元数据与列表能力（t148）
 *
 * <p>三个 t146 前端暴露的缺口：
 * <ol>
 *   <li>列表没有「上传人」（响应只回实体，没有 uploaderName）→ 本次每项带 `uploadedBy` + `uploaderName`；</li>
 *   <li>上传没有独立备注 → 新增 `remark`（≤500，超限 400）；</li>
 *   <li>列表无分页/筛选/排序 → 改为分页结构 + sort 白名单 + keyword/category/uploaderId/dateFrom/dateTo。</li>
 * </ol>
 *
 * <p>兼容口径：**选方案①**——不传参数时 `page=0&size=20`，响应带 `totalElements`；
 * 旧调用（只认数组）需改成读 `data.content`，清单见 README §66.5。
 */
@DisplayName("资产文件元数据与列表测试")
public class AssetFileMetaTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetFileRepository assetFileRepository;
    @Autowired
    private AssetDeliverableRepository assetDeliverableRepository;
    @Autowired
    private AssetVersionRepository assetVersionRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private com.vulnaudit.repository.UserRepository userRepository;

    private Asset asset;
    private UUID uploaderId;

    @BeforeEach
    void setUpFixtures() {
        asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t148 文件资产")
                .code("T148-" + System.nanoTime())
                .targetType("web")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .build()));
        uploaderId = adminUserId;
    }

    // ------------------------------------------------------------------ 工具

    private MockMultipartFile payload(String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream",
                ("content-of-" + filename).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode uploadFile(String filename, String category, String remark) throws Exception {
        var request = multipart("/api/v1/assets/" + asset.getId() + "/files")
                .file(payload(filename))
                .param("category", category)
                .header("Authorization", adminToken);
        if (remark != null) {
            request = request.param("remark", remark);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode uploadDeliverable(String filename, String type, String remark) throws Exception {
        var request = multipart("/api/v1/assets/" + asset.getId() + "/deliverables")
                .file(payload(filename))
                .param("deliverableType", type)
                .header("Authorization", adminToken);
        if (remark != null) {
            request = request.param("remark", remark);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private AssetFile legacyFile(String name, String category, UUID uploadedBy, LocalDateTime createdAt) {
        return legacyFile(name, category, uploadedBy, createdAt, 1);
    }

    /** t151：可指定文件版本号，用于「版本列」排序用例 */
    private AssetFile legacyFile(String name, String category, UUID uploadedBy, LocalDateTime createdAt,
                                 int version) {
        AssetFile file = assetFileRepository.save(Objects.requireNonNull(AssetFile.builder()
                .assetId(asset.getId())
                .name(name)
                .category(category)
                .filePath("./uploads/assets/legacy/" + name)
                .fileSize(100L)
                .version(version)
                .status("active")
                .uploadedBy(uploadedBy)
                .build()));
        // 直接改 created_at，构造时间区间筛选的夹具
        assetFileRepository.flush();
        entityManager.createNativeQuery("update asset_files set created_at = :ts where id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", file.getId())
                .executeUpdate();
        entityManager.clear();
        return assetFileRepository.findById(file.getId()).orElseThrow();
    }

    private JsonNode listFiles(String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/files" + query)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long queryCount() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return stats.getPrepareStatementCount();
    }

    // ------------------------------------------------------------------ 一、上传元数据

    @Test
    @DisplayName("上传：remark 落库、uploaded_by = 当前用户")
    void uploadPersistsRemarkAndUploader() throws Exception {
        JsonNode created = uploadFile("带备注.txt", "document", "这是独立备注");
        assertEquals("这是独立备注", created.path("remark").asText());
        assertEquals(uploaderId.toString(), created.path("uploadedBy").asText());

        AssetFile row = assetFileRepository.findById(UUID.fromString(created.path("id").asText())).orElseThrow();
        assertEquals("这是独立备注", row.getRemark());
        assertEquals(uploaderId, row.getUploadedBy());

        // 列表里也能看到 remark + 上传人展示名
        JsonNode data = listFiles("?allVersions=true");
        JsonNode item = data.path("content").get(0);
        assertEquals("这是独立备注", item.path("remark").asText());
        assertEquals(usernameOf(adminUserId), item.path("uploaderName").asText());
    }

    @Test
    @DisplayName("上传：remark 超过 500 字符 → 400")
    void uploadRejectsTooLongRemark() throws Exception {
        String tooLong = "x".repeat(501);
        mockMvc.perform(multipart("/api/v1/assets/" + asset.getId() + "/files")
                        .file(payload("too-long.txt"))
                        .param("category", "document")
                        .param("remark", tooLong)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .file(payload("too-long.py"))
                        .param("deliverableType", "POC")
                        .param("remark", tooLong)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("上传：remark 正好 500 字符 → 允许（边界不误伤）")
    void uploadAcceptsBoundaryRemark() throws Exception {
        String boundary = "y".repeat(500);
        JsonNode created = uploadFile("boundary.txt", "document", boundary);
        assertEquals(500, created.path("remark").asText().length());
    }

    @Test
    @DisplayName("上传交付物：remark 落库、每项带 uploaderName")
    void uploadDeliverablePersistsRemark() throws Exception {
        JsonNode created = uploadDeliverable("poc.py", "POC脚本", "交付物备注");
        assertEquals("交付物备注", created.path("remark").asText());

        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].remark").value("交付物备注"))
                .andExpect(jsonPath("$.data.content[0].uploaderName").value(usernameOf(adminUserId)))
                .andReturn().getResponse().getContentAsString();
        assertNotNull(objectMapper.readTree(body).path("data").path("totalElements"));
    }

    // ------------------------------------------------------------------ 二、分页 + 排序

    @Test
    @DisplayName("列表：默认分页 20（方案①），total/content 一致")
    void listDefaultsToPageSize20() throws Exception {
        for (int i = 0; i < 25; i++) {
            uploadFile("bulk-" + i + ".txt", "document", null);
        }
        JsonNode data = listFiles("?allVersions=true");

        assertEquals(25, data.path("totalElements").asInt(), "总数应是 25（不受分页影响）");
        assertEquals(20, data.path("content").size(), "默认每页 20 条");
        assertEquals(0, data.path("currentPage").asInt());
        assertEquals(20, data.path("pageSize").asInt());
        assertEquals(2, data.path("totalPages").asInt());
    }

    @Test
    @DisplayName("列表：page/size 生效，totalElements 稳定")
    void listRespectsPageAndSize() throws Exception {
        for (int i = 0; i < 7; i++) {
            uploadFile("page-" + i + ".txt", "document", null);
        }
        JsonNode first = listFiles("?allVersions=true&page=0&size=5");
        JsonNode second = listFiles("?allVersions=true&page=1&size=5");

        assertEquals(7, first.path("totalElements").asInt());
        assertEquals(5, first.path("content").size());
        assertEquals(2, second.path("content").size());
        assertEquals(1, second.path("currentPage").asInt());
    }

    @Test
    @DisplayName("列表：size 超过 100 → 400")
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/files")
                        .param("size", "101").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("排序：sort=uploadedAt,desc 生效；name,asc / size,desc 也生效")
    void sortWhitelistWorks() throws Exception {
        legacyFile("a-old.txt", "document", uploaderId, LocalDateTime.now().minusDays(3));
        legacyFile("z-new.txt", "document", uploaderId, LocalDateTime.now().minusDays(1));

        JsonNode desc = listFiles("?allVersions=true&sort=uploadedAt,desc");
        assertEquals("z-new.txt", desc.path("content").get(0).path("name").asText(), "uploadedAt 映射到 created_at 倒序");

        JsonNode byName = listFiles("?allVersions=true&sort=name,asc");
        assertEquals("a-old.txt", byName.path("content").get(0).path("name").asText());

        JsonNode bySize = listFiles("?allVersions=true&sort=size,desc");
        assertTrue(bySize.path("content").get(0).path("fileSize").asLong()
                >= bySize.path("content").get(1).path("fileSize").asLong());
    }

    @Test
    @DisplayName("排序：非法字段 / 非法方向 → 400")
    void sortRejectsIllegalField() throws Exception {
        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/files")
                        .param("sort", "passwordHash,desc").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/files")
                        .param("sort", "name,sideways").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("sort", "unknown,asc").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("排序：非法字段 400 的消息里带**完整**可选值列表（t151）")
    void sortErrorMessageListsAllOptions() throws Exception {
        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/files")
                        .param("sort", "passwordHash,desc").header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // 前端要能据此对齐，故逐个断言白名单里每个键都出现在消息里
        for (String option : java.util.List.of("uploadedAt", "createdAt", "name", "size", "fileSize",
                "category", "assetVersionId", "version", "uploadedBy", "uploaderId")) {
            assertTrue(body.contains(option), "错误消息应含可选值 " + option + "，实际: " + body);
        }

        String deliverableBody = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("sort", "nope,asc").header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        for (String option : java.util.List.of("uploadedAt", "name", "size", "type", "deliverableType",
                "assetVersionId", "uploadedBy", "uploaderId")) {
            assertTrue(deliverableBody.contains(option),
                    "交付物错误消息应含可选值 " + option + "，实际: " + deliverableBody);
        }
    }

    @Test
    @DisplayName("排序 t151：assetVersionId,asc 按文件版本号排序，且第 2 页首行正确（跨页）")
    void sortByAssetVersionIdAcrossPages() throws Exception {
        // 25 条、版本号 1..25 打乱写入，分页后第 2 页首行应为版本 21
        for (int i = 25; i >= 1; i--) {
            legacyFile("ver-" + i + ".txt", "document", uploaderId, LocalDateTime.now().minusMinutes(i), i);
        }

        JsonNode page0 = listFiles("?allVersions=true&sort=assetVersionId,asc&size=20&page=0");
        JsonNode page1 = listFiles("?allVersions=true&sort=assetVersionId,asc&size=20&page=1");

        assertEquals(25, page0.path("totalElements").asInt());
        assertEquals(1, page0.path("content").get(0).path("version").asInt(),
                "第 1 页首行应是版本 1（按版本号而非 UUID 排序）");
        assertEquals(20, page0.path("content").size());
        assertEquals(5, page1.path("content").size());
        assertEquals(21, page1.path("content").get(0).path("version").asInt(),
                "第 2 页首行应是版本 21（证明排序在数据库层、跨页有效）");

        // 别名 version 等价
        JsonNode byAlias = listFiles("?allVersions=true&sort=version,asc&size=20&page=1");
        assertEquals(21, byAlias.path("content").get(0).path("version").asInt());
    }

    @Test
    @DisplayName("排序 t151：uploadedBy,desc 生效且第 2 页首行正确（跨页，size=1）")
    void sortByUploadedByAcrossPages() throws Exception {
        // 三个上传人，各一页；desc 时第 2 页应是「UUID 居中」的那个人
        java.util.List<UUID> uploaders = java.util.List.of(uploaderId, engineerUserId, leaderUserId);
        for (int i = 0; i < uploaders.size(); i++) {
            legacyFile("by-" + i + ".txt", "document", uploaders.get(i), LocalDateTime.now().minusMinutes(i));
        }
        java.util.List<UUID> sortedDesc = new java.util.ArrayList<>(uploaders);
        sortedDesc.sort(java.util.Comparator.comparing(UUID::toString).reversed());

        JsonNode page0 = listFiles("?allVersions=true&sort=uploadedBy,desc&size=1&page=0");
        JsonNode page1 = listFiles("?allVersions=true&sort=uploadedBy,desc&size=1&page=1");

        assertEquals(3, page0.path("totalElements").asInt());
        assertEquals(sortedDesc.get(0).toString(), page0.path("content").get(0).path("uploadedBy").asText());
        assertEquals(sortedDesc.get(1).toString(), page1.path("content").get(0).path("uploadedBy").asText(),
                "第 2 页应是 UUID 排序的后一位（排序在数据库层，不是页内排序）");

        // 别名 uploaderId 等价
        JsonNode byAlias = listFiles("?allVersions=true&sort=uploaderId,desc&size=1&page=1");
        assertEquals(sortedDesc.get(1).toString(), byAlias.path("content").get(0).path("uploadedBy").asText());
    }

    @Test
    @DisplayName("排序 t151：交付物 assetVersionId / uploadedBy 也可服务端排序")
    void deliverableNewSortKeysWork() throws Exception {
        uploadDeliverable("d-1.py", "POC脚本", null);
        uploadDeliverable("d-2.py", "POC脚本", null);

        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").param("sort", "uploadedBy,asc")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, objectMapper.readTree(body).path("data").path("totalElements").asInt());

        // assetVersionId 对交付物是 UUID 列，仅要求不 400 且结果稳定
        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").param("sort", "assetVersionId,desc")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 三、筛选

    @Test
    @DisplayName("筛选：keyword 文件名模糊")
    void filterByKeyword() throws Exception {
        legacyFile("report-final.pdf", "document", uploaderId, LocalDateTime.now());
        legacyFile("scan-notes.txt", "document", uploaderId, LocalDateTime.now());

        JsonNode data = listFiles("?allVersions=true&keyword=report");
        assertEquals(1, data.path("totalElements").asInt());
        assertEquals("report-final.pdf", data.path("content").get(0).path("name").asText());
    }

    @Test
    @DisplayName("筛选：category 精确匹配")
    void filterByCategory() throws Exception {
        legacyFile("doc-1.txt", "document", uploaderId, LocalDateTime.now());
        legacyFile("tool-1.zip", "tool", uploaderId, LocalDateTime.now());

        JsonNode data = listFiles("?allVersions=true&category=tool");
        assertEquals(1, data.path("totalElements").asInt());
        assertEquals("tool-1.zip", data.path("content").get(0).path("name").asText());
    }

    @Test
    @DisplayName("筛选：uploaderId 只返回该上传人的文件")
    void filterByUploader() throws Exception {
        legacyFile("mine.txt", "document", uploaderId, LocalDateTime.now());
        // 另一个用户（工程师）上传的文件
        legacyFile("others.txt", "document", engineerUserId, LocalDateTime.now());

        JsonNode mine = listFiles("?allVersions=true&uploaderId=" + uploaderId);
        assertEquals(1, mine.path("totalElements").asInt());
        assertEquals("mine.txt", mine.path("content").get(0).path("name").asText());

        JsonNode others = listFiles("?allVersions=true&uploaderId=" + engineerUserId);
        assertEquals(1, others.path("totalElements").asInt());
        assertEquals(usernameOf(engineerUserId), others.path("content").get(0).path("uploaderName").asText());
    }

    @Test
    @DisplayName("筛选：dateFrom/dateTo 是闭区间（含当日）")
    void filterByDateRange() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        legacyFile("old.txt", "document", uploaderId, now.minusDays(10));
        legacyFile("recent.txt", "document", uploaderId, now.minusHours(2));

        String from = now.minusDays(1).toLocalDate().toString();
        String to = now.toLocalDate().toString();
        JsonNode data = listFiles("?allVersions=true&dateFrom=" + from + "&dateTo=" + to);

        assertEquals(1, data.path("totalElements").asInt(), "只应命中今天上传的那条");
        assertEquals("recent.txt", data.path("content").get(0).path("name").asText());
    }

    // ------------------------------------------------------------------ 四、上传人展示名 + 性能

    @Test
    @DisplayName("上传人：uploaderName 正确解析，未知上传人 → null（不报错）")
    void uploaderNameResolved() throws Exception {
        uploadFile("with-uploader.txt", "document", null);
        legacyFile("orphan-uploader.txt", "document", UUID.randomUUID(), LocalDateTime.now().minusDays(1));

        JsonNode data = listFiles("?allVersions=true&size=50");
        boolean sawNamed = false;
        boolean sawNull = false;
        for (JsonNode item : data.path("content")) {
            if ("with-uploader.txt".equals(item.path("name").asText())) {
                assertEquals(usernameOf(adminUserId), item.path("uploaderName").asText());
                sawNamed = true;
            }
            if ("orphan-uploader.txt".equals(item.path("name").asText())) {
                assertTrue(item.path("uploaderName").isNull(), "查不到上传人时应为 null: " + item);
                sawNull = true;
            }
        }
        assertTrue(sawNamed && sawNull, "两条都应出现在列表里");
    }

    @Test
    @DisplayName("性能：列表查询数不随页大小增长（uploaderName 一次批量）")
    void queryCountDoesNotGrowWithPageSize() throws Exception {
        for (int i = 0; i < 25; i++) {
            legacyFile("perf-" + i + ".txt", "document", uploaderId, LocalDateTime.now().minusMinutes(i));
        }
        // 预热
        listFiles("?allVersions=true&size=5");

        long before5 = queryCount();
        listFiles("?allVersions=true&size=5");
        long delta5 = queryCount() - before5;

        long before20 = queryCount();
        JsonNode twenty = listFiles("?allVersions=true&size=20");
        long delta20 = queryCount() - before20;

        assertEquals(20, twenty.path("content").size());
        assertEquals(delta5, delta20,
                "查询数必须与页大小无关（size=5: " + delta5 + "，size=20: " + delta20 + "）");
        assertTrue(delta20 <= 8, "单次列表请求应是常数级，实测 " + delta20 + " 次");
    }

    // ------------------------------------------------------------------ 五、旧数据与迁移脚本

    @Test
    @DisplayName("旧数据：只剩 uploaded_by、无 remark 的历史行仍能列出（remark 为 null）")
    void legacyRowsStillListed() throws Exception {
        AssetFile legacy = assetFileRepository.save(Objects.requireNonNull(AssetFile.builder()
                .assetId(asset.getId())
                .name("legacy-no-remark.txt")
                .category("document")
                .filePath("./uploads/assets/legacy/legacy-no-remark.txt")
                .fileSize(10L)
                .version(1)
                .status("active")
                .uploadedBy(uploaderId)
                .build()));

        JsonNode data = listFiles("?allVersions=true");
        JsonNode item = data.path("content").get(0);
        assertEquals(legacy.getId().toString(), item.path("id").asText());
        assertTrue(item.path("remark").isNull(), "历史行 remark 应为 null");
        assertEquals(usernameOf(adminUserId), item.path("uploaderName").asText(), "历史行 uploaded_by 仍能解析成展示名");
    }

    @Test
    @DisplayName("迁移脚本 V31：加 remark 列 + 排序索引 + 覆盖率校验（幂等守卫）")
    void migrationScriptGuards() throws Exception {
        String sql = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/db/migration/V31__asset_file_metadata.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ALTER TABLE asset_files ADD COLUMN IF NOT EXISTS remark VARCHAR(500)"));
        assertTrue(sql.contains("ALTER TABLE asset_deliverables ADD COLUMN IF NOT EXISTS remark VARCHAR(500)"));
        assertTrue(sql.contains("idx_asset_files_asset_created"), "排序索引");
        assertTrue(sql.contains("idx_asset_deliverables_asset_created"), "排序索引");
        assertTrue(sql.contains("uploaded_by IS NULL"), "应有 uploaded_by 覆盖率校验");
        assertTrue(manualDdl().contains("V31：资产文件 / 交付物补齐元数据"), "manual-ddl 必须有等价段");
    }

    @Test
    @DisplayName("交付物列表：分页 + keyword + type 排序同样生效")
    void deliverableListPagination() throws Exception {
        uploadDeliverable("alpha-poc.py", "POC脚本", "第一份");
        uploadDeliverable("beta-doc.md", "说明文档", "第二份");

        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").param("sort", "name,asc")
                        .param("size", "1").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("alpha-poc.py"))
                .andReturn().getResponse().getContentAsString();
        assertNotNull(objectMapper.readTree(body).path("data").path("pageSize"));
    }

    @Test
    @DisplayName("交付物筛选：category（=交付物类型）与 uploaderId")
    void deliverableFilters() throws Exception {
        uploadDeliverable("poc.py", "POC脚本", null);
        uploadDeliverable("readme.md", "说明文档", null);

        String body = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").param("category", "POC脚本")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(1, data.path("totalElements").asInt());
        assertEquals("poc.py", data.path("content").get(0).path("name").asText());

        String byUploader = mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/deliverables")
                        .param("allVersions", "true").param("uploaderId", adminUserId.toString())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, objectMapper.readTree(byUploader).path("data").path("totalElements").asInt());
    }

    @Test
    @DisplayName("版本回退：指定版本无文件时回落到「无版本」文件（保持旧语义）")
    void versionFallbackPreserved() throws Exception {
        AssetVersion version = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(asset.getId())
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .isLatest(true)
                .status("active")
                .build()));
        legacyFile("no-version.txt", "document", uploaderId, LocalDateTime.now());

        JsonNode data = listFiles("?assetVersionId=" + version.getId());
        assertEquals(1, data.path("totalElements").asInt());
        assertEquals("no-version.txt", data.path("content").get(0).path("name").asText());
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getUsername();
    }

    private String manualDdl() throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of("../deploy/manual-ddl.sql"),
                StandardCharsets.UTF_8);
    }
}
