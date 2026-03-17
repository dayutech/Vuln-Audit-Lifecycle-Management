package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assets")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 50)
    private String code;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private AssetType type;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private AssetStatus status;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private AssetPriority priority;

    @Column(length = 1000)
    private String description;

    @Column(length = 200)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamEntity team;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum AssetType {
        web, mobile, api, iot, network, other
    }

    public enum AssetStatus {
        available, in_progress, completed, archived
    }

    public enum AssetPriority {
        critical, high, medium, low
    }
}
