package com.vulnaudit.repository;

import com.vulnaudit.entity.VerificationCode;
import com.vulnaudit.entity.enums.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 验证码仓储（t102）
 */
@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    /** 最近一次该用途的验证码（含已消费，用于限流判断） */
    Optional<VerificationCode> findFirstByTargetAndPurposeOrderByCreatedAtDesc(String target, VerificationPurpose purpose);

    /** 最近一次未消费的验证码（用于校验） */
    Optional<VerificationCode> findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String target, VerificationPurpose purpose);

    /** 指定时间之后该 target+purpose 的发送次数（小时级上限） */
    long countByTargetAndPurposeAndCreatedAtAfter(String target, VerificationPurpose purpose, LocalDateTime after);
}
