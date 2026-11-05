package com.vulnaudit.repository;

import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注册申请仓储（t102）
 */
@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    Page<RegistrationRequest> findByStatus(RegistrationStatus status, Pageable pageable);

    Optional<RegistrationRequest> findFirstByUsernameAndStatusOrderByCreatedAtDesc(String username, RegistrationStatus status);

    Optional<RegistrationRequest> findFirstByPhoneAndStatusOrderByCreatedAtDesc(String phone, RegistrationStatus status);

    Optional<RegistrationRequest> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, RegistrationStatus status);

    boolean existsByUsernameAndStatus(String username, RegistrationStatus status);

    boolean existsByPhoneAndStatus(String phone, RegistrationStatus status);

    boolean existsByEmailAndStatus(String email, RegistrationStatus status);

    List<RegistrationRequest> findByUsername(String username);
}
