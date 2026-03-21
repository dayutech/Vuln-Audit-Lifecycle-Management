package com.vulnaudit.repository;

import com.vulnaudit.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 任务评论Repository接口
 */
@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    /**
     * 按任务ID查询评论列表
     */
    List<TaskComment> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    /**
     * 按用户ID查询评论列表
     */
    List<TaskComment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 统计任务评论数量
     */
    long countByTaskId(UUID taskId);
}
