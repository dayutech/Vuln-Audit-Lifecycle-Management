package com.vulnaudit.repository;

import com.vulnaudit.entity.Task;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    List<Task> findByAssignedTo(@Nullable UUID assignedTo);
    List<Task> findByAssignedBy(@Nullable UUID assignedBy);
    List<Task> findByAssetId(@Nullable UUID assetId);
    List<Task> findByVulnerabilityId(@Nullable UUID vulnerabilityId);
    List<Task> findByStatus(@Nullable String status);
    List<Task> findByAssignedToAndStatus(@Nullable UUID assignedTo, @Nullable String status);
    List<Task> findByReferenceId(@Nullable UUID referenceId);

    /**
     * 批量统计各项目下的「未完成任务数」（t82，一次 group by）
     *
     * <p>任务本身没有 projectId（挂在资产/漏洞上），因此按「任务的资产 ∈ 该项目的挂靠资产」关联；
     * 未完成 = 排除 {@code completed} / {@code cancelled}。
     *
     * @return 每行 {@code [projectId(UUID), count(Long)]}
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.projectId, count(distinct t.id) from Task t, AssetProjectLink l "
                    + "where t.assetId = l.assetId and l.projectId in :projectIds "
                    + "and t.status not in ('completed', 'cancelled') "
                    + "group by l.projectId")
    List<Object[]> countOpenTasksGroupedByProjectIds(@org.springframework.data.repository.query.Param("projectIds")
                                                     java.util.Collection<UUID> projectIds);

}
