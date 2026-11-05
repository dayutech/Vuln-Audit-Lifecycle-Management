package com.vulnaudit.repository;

import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注册申请仓储（t102 / t111）
 */
@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    /**
     * 按 ID 加行级写锁读取（t111）
     *
     * <p>审批 / 拒绝 / 清理三条路径并发时靠行锁串行化：后到者看到已变更的状态 → 返回 409（先到者为准）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.id = :id")
    Optional<RegistrationRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<RegistrationRequest> findByStatus(RegistrationStatus status, Pageable pageable);

    Optional<RegistrationRequest> findFirstByUsernameAndStatusOrderByCreatedAtDesc(String username, RegistrationStatus status);

    Optional<RegistrationRequest> findFirstByPhoneAndStatusOrderByCreatedAtDesc(String phone, RegistrationStatus status);

    Optional<RegistrationRequest> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, RegistrationStatus status);

    boolean existsByUsernameAndStatus(String username, RegistrationStatus status);

    boolean existsByPhoneAndStatus(String phone, RegistrationStatus status);

    boolean existsByEmailAndStatus(String email, RegistrationStatus status);

    List<RegistrationRequest> findByUsername(String username);
}
