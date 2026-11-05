package com.vulnaudit.repository;

import com.vulnaudit.entity.SsoPendingBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO 待审批绑定仓储（t104）
 */
@Repository
public interface SsoPendingBindingRepository extends JpaRepository<SsoPendingBinding, UUID> {

    Optional<SsoPendingBinding> findByProviderCodeAndExternalId(String providerCode, String externalId);

    List<SsoPendingBinding> findByRegistrationRequestId(UUID registrationRequestId);

    List<SsoPendingBinding> findByRegistrationRequestIdAndStatus(UUID registrationRequestId, String status);
}
