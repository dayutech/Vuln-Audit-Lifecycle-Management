package com.vulnaudit.repository;

import com.vulnaudit.entity.EnvironmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentTemplateRepository extends JpaRepository<EnvironmentTemplate, UUID> {

    List<EnvironmentTemplate> findByType(String type);
    List<EnvironmentTemplate> findByCreatedBy(UUID createdBy);
    Optional<EnvironmentTemplate> findByName(String name);

}
