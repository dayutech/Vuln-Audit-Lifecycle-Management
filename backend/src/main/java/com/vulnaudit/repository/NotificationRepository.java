package com.vulnaudit.repository;

import com.vulnaudit.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByType(String type);
    List<Notification> findBySenderId(UUID senderId);
    
    @Query("SELECT n FROM Notification n JOIN NotificationRecipient nr ON n.id = nr.notificationId WHERE nr.userId = :userId ORDER BY n.createdAt DESC")
    List<Notification> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT n FROM Notification n JOIN NotificationRecipient nr ON n.id = nr.notificationId WHERE nr.userId = :userId AND nr.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserId(@Param("userId") UUID userId);

}
