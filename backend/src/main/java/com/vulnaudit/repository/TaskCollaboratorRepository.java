package com.vulnaudit.repository;

import com.vulnaudit.entity.TaskCollaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCollaboratorRepository extends JpaRepository<TaskCollaborator, UUID> {
    List<TaskCollaborator> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    List<TaskCollaborator> findByUserId(UUID userId);

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskIdAndUserId(UUID taskId, UUID userId);
}
