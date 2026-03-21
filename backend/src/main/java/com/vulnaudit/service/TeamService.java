package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 团队服务接口
 */
public interface TeamService {

    Team createTeam(Team team);

    Optional<Team> findById(UUID id);

    Optional<Team> findByName(String name);

    PageResponse<Team> findAll(int page, int size, String keyword);

    List<Team> findAll();

    Team updateTeam(UUID id, Team team);

    void deleteTeam(UUID id);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    List<Team> findByParentId(UUID parentId);

    List<Team> findRootTeams();

    List<Team> findTeamTree(UUID teamId);

    void addMember(UUID teamId, UUID userId, String role);

    void removeMember(UUID teamId, UUID userId);

    List<UUID> getMemberIds(UUID teamId);

    List<UUID> getTeamUserIds(UUID teamId);

    boolean isMember(UUID teamId, UUID userId);

    boolean isLeader(UUID teamId, UUID userId);
}
