package com.vulnaudit.repository;

import com.vulnaudit.entity.SsoProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO 提供商仓储（t104）
 */
@Repository
public interface SsoProviderRepository extends JpaRepository<SsoProvider, UUID> {

    Optional<SsoProvider> findByCode(String code);

    List<SsoProvider> findByEnabledTrue();

    List<SsoProvider> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);
}
