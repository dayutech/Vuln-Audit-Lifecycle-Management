package com.vulnaudit.repository;

import com.vulnaudit.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知设置Repository接口
 */
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {

    /**
     * 按用户ID查询所有设置
     */
    List<NotificationSetting> findByUserId(UUID userId);

    /**
     * 按用户ID和通知类型查询
     */
    Optional<NotificationSetting> findByUserIdAndType(UUID userId, String type);

    /**
     * 删除用户的所有设置
     */
    void deleteByUserId(UUID userId);

    /**
     * 删除用户的特定类型设置
     */
    void deleteByUserIdAndType(UUID userId, String type);
}
