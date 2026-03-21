package com.vulnaudit.repository;

import com.vulnaudit.entity.SystemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 系统公告Repository接口
 */
@Repository
public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, UUID> {

    /**
     * 按优先级查询
     */
    List<SystemAnnouncement> findByPriorityOrderByPublishedAtDesc(String priority);

    /**
     * 查询已发布且未过期的公告
     */
    @Query("SELECT s FROM SystemAnnouncement s WHERE s.publishedAt <= :now AND (s.expiresAt IS NULL OR s.expiresAt > :now) ORDER BY s.publishedAt DESC")
    List<SystemAnnouncement> findActive(LocalDateTime now);

    /**
     * 查询指定时间之后发布的公告
     */
    List<SystemAnnouncement> findByPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime publishedAt);
}
