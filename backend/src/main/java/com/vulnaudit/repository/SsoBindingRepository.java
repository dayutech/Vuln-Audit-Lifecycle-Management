package com.vulnaudit.repository;

import com.vulnaudit.entity.SsoBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO 绑定仓储（t104）
 */
@Repository
public interface SsoBindingRepository extends JpaRepository<SsoBinding, UUID> {

    Optional<SsoBinding> findByProviderCodeAndExternalId(String providerCode, String externalId);

    Optional<SsoBinding> findByUserIdAndProviderCode(UUID userId, String providerCode);

    List<SsoBinding> findByUserId(UUID userId);

    long countByUserId(UUID userId);
}
