package com.vulnaudit.repository;

import com.vulnaudit.entity.NotificationRecipient;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {

    List<NotificationRecipient> findByNotificationId(@Nullable UUID notificationId);
    List<NotificationRecipient> findByUserId(@Nullable UUID userId);
    Page<NotificationRecipient> findByUserId(@Nullable UUID userId, Pageable pageable);
    Page<NotificationRecipient> findByUserIdAndIsRead(@Nullable UUID userId, @Nullable Boolean isRead, Pageable pageable);
    List<NotificationRecipient> findByUserIdAndIsRead(@Nullable UUID userId, @Nullable Boolean isRead);
    Optional<NotificationRecipient> findByNotificationIdAndUserId(@Nullable UUID notificationId, @Nullable UUID userId);

    @Query("SELECT nr FROM NotificationRecipient nr WHERE nr.userId = :userId AND nr.isRead = false")
    List<NotificationRecipient> findUnreadByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.userId = :userId AND nr.isRead = false")
    long countUnreadByUserId(@Param("userId") UUID userId);

    long countByUserIdAndIsRead(@Nullable UUID userId, @Nullable Boolean isRead);

    @Modifying
    @Query("UPDATE NotificationRecipient nr SET nr.isRead = true, nr.readAt = CURRENT_TIMESTAMP WHERE nr.userId = :userId AND nr.isRead = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId);

}
