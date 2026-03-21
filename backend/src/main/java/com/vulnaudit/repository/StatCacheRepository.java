package com.vulnaudit.repository;

import com.vulnaudit.entity.StatCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 统计缓存Repository接口
 */
@Repository
public interface StatCacheRepository extends JpaRepository<StatCache, UUID> {

    /**
     * 按统计类型查询
     */
    List<StatCache> findByStatType(String statType);

    /**
     * 按统计类型和维度查询
     */
    Optional<StatCache> findByStatTypeAndStatKey(String statType, String statKey);

    /**
     * 删除指定时间之前的缓存
     */
    void deleteByCalculatedAtBefore(LocalDateTime calculatedAt);

    /**
     * 删除指定类型的缓存
     */
    void deleteByStatType(String statType);
}
