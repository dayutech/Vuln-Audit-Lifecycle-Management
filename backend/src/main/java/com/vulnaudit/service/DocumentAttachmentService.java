package com.vulnaudit.service;

import com.vulnaudit.dto.DocumentAttachmentItem;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 知识库 / 文档附件（t162）
 *
 * <p>取代前端「正文内 base64 附件块」方案（受 `content TEXT(64KB)` 限制，二进制实际只能 ≤40KB）：
 * 附件独立成行 + 文件落盘（UUID 前缀），单文件上限 50MB，超限 **413**。
 *
 * <p>安全：文件名净化 + 磁盘名加 UUID 前缀；**没有按 storage_path 直接下载的端点**，
 * 下载必须经 {@link #download} 并做「归属校验」（附件不属于该实体 → 404）。
 */
public interface DocumentAttachmentService {

    /** 单文件上限（50MB），供文档/测试断言 */
    long maxAttachmentBytes();

    DocumentAttachmentItem upload(String ownerType, UUID ownerId, MultipartFile file, UUID operatorId);

    List<DocumentAttachmentItem> list(String ownerType, UUID ownerId);

    /** 下载载荷（路径 + 原始文件名 + MIME），调用方负责写响应流 */
    AttachmentDownload download(String ownerType, UUID ownerId, UUID attachmentId);

    /** 软删 + 删物理文件 */
    void delete(String ownerType, UUID ownerId, UUID attachmentId, UUID operatorId);

    /** 下载载荷 */
    record AttachmentDownload(Path path, String fileName, String contentType, long size) {
    }
}
