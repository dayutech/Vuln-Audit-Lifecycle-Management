package com.vulnaudit.repository;

import com.vulnaudit.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    List<Task> findByAssignedTo(UUID assignedTo);
    List<Task> findByAssignedBy(UUID assignedBy);
    List<Task> findByAssetId(UUID assetId);
    List<Task> findByVulnerabilityId(UUID vulnerabilityId);
    List<Task> findByStatus(String status);
    List<Task> findByAssignedToAndStatus(UUID assignedTo, String status);
    List<Task> findByReferenceId(UUID referenceId);

}
