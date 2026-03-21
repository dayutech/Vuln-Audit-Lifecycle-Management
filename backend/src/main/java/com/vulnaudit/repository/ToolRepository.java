package com.vulnaudit.repository;

import com.vulnaudit.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID>, JpaSpecificationExecutor<Tool> {

    List<Tool> findByCategory(String category);
    List<Tool> findByCreatedBy(UUID createdBy);
    List<Tool> findByIsOfficial(Boolean isOfficial);
    Optional<Tool> findByName(String name);
    boolean existsByName(String name);
    
    @Query("SELECT t FROM Tool t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Tool> searchByKeyword(@Param("keyword") String keyword);

}
