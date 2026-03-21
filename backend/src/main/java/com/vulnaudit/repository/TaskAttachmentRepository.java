package com.vulnaudit.repository;

import com.vulnaudit.entity.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 任务附件Repository接口
 */
@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {

    /**
     * 按任务ID查询附件列表
     */
    List<TaskAttachment> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    /**
     * 按上传者ID查询附件列表
     */
    List<TaskAttachment> findByUploadedByOrderByCreatedAtDesc(UUID uploadedBy);
}
