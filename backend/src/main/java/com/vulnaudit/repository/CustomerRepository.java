package com.vulnaudit.repository;

import com.vulnaudit.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 客户仓储（t67）
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByCode(String code);

    boolean existsByCode(String code);

    List<Customer> findByStatus(String status);

    List<Customer> findByOwnerId(UUID ownerId);
}
