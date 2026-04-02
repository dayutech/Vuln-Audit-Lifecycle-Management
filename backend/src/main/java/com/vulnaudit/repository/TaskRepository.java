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

}
