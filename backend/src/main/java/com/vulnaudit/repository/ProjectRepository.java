package com.vulnaudit.repository;

import com.vulnaudit.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目仓储（t67）
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Project> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    /** 某客户下的全部项目 */
    List<Project> findByCustomerId(UUID customerId);

    /** 某人负责（项目经理）的全部项目 */
    List<Project> findByManagerId(UUID managerId);

    List<Project> findByCustomerIdAndStatus(UUID customerId, String status);

    List<Project> findByStatus(String status);
}
