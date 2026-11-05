package com.vulnaudit.repository;

import com.vulnaudit.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * 系统设置仓储（t109）
 */
@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

    Optional<SystemSetting> findBySettingKey(@Nullable String settingKey);

    boolean existsBySettingKey(@Nullable String settingKey);
}
