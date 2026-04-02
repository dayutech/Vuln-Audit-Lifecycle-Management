package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Team;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 团队服务接口
 */
public interface TeamService {

    @Nonnull
    Team createTeam(@Nonnull Team team);

    Optional<Team> findById(@Nullable UUID id);

    Optional<Team> findByName(@Nullable String name);

    @Nonnull
    PageResponse<Team> findAll(int page, int size, @Nullable String keyword);

    @Nonnull
    List<Team> findAll();

    @Nonnull
    Team updateTeam(@Nullable UUID id, @Nonnull Team team);

    void deleteTeam(@Nullable UUID id);

    boolean existsByName(@Nullable String name);

    @Nonnull
    List<Team> findByParentId(@Nullable UUID parentId);

    @Nonnull
    List<Team> findRootTeams();

    @Nonnull
    List<Team> findTeamTree(@Nullable UUID teamId);

    void addMember(@Nullable UUID teamId, @Nullable UUID userId, @Nullable String role);

    void removeMember(@Nullable UUID teamId, @Nullable UUID userId);

    @Nonnull
    List<UUID> getMemberIds(@Nullable UUID teamId);

    @Nonnull
    List<UUID> getTeamUserIds(@Nullable UUID teamId);

    boolean isMember(@Nullable UUID teamId, @Nullable UUID userId);

    boolean isLeader(@Nullable UUID teamId, @Nullable UUID userId);
}