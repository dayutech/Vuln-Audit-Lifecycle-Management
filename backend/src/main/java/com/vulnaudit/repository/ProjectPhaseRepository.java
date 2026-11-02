package com.vulnaudit.repository;

import com.vulnaudit.entity.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目期次仓储（t67）
 */
@Repository
public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProjectPhase> {

    /** 某项目的期次，按期次号升序（页面/报表默认顺序） */
    List<ProjectPhase> findByProjectIdOrderByPhaseNo(UUID projectId);

    Optional<ProjectPhase> findByProjectIdAndPhaseNo(UUID projectId, Integer phaseNo);

    boolean existsByProjectIdAndPhaseNo(UUID projectId, Integer phaseNo);

    List<ProjectPhase> findByProjectIdAndStatus(UUID projectId, String status);

    /**
     * 批量统计各项目的期次数（t76）
     *
     * <p><b>一次 group by 查询</b>算出整页项目（客户详情里的全部项目）的期次数，
     * 禁止“逐项目 findById + size()”的 N+1 写法。
     *
     * @return 每行 {@code [projectId(UUID), count(Long)]}；无期次的项目不出现在结果中
     */
    @Query("select p.projectId, count(p) from ProjectPhase p where p.projectId in :projectIds "
            + "group by p.projectId")
    List<Object[]> countGroupedByProjectIds(@Param("projectIds") java.util.Collection<UUID> projectIds);
}
