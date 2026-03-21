package com.vulnaudit.repository;

import com.vulnaudit.entity.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 仪表盘配置Repository接口
 */
@Repository
public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, UUID> {

    /**
     * 按用户ID查询配置
     */
    Optional<DashboardConfig> findByUserId(UUID userId);

    /**
     * 删除用户配置
     */
    void deleteByUserId(UUID userId);

    /**
     * 检查用户是否有配置
     */
    boolean existsByUserId(UUID userId);
}
