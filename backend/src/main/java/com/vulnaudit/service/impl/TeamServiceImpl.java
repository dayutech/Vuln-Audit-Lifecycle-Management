package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.TeamService;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 团队服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Team createTeam(Team team) {
        log.info("创建团队: name={}", team.getName());
        
        if (teamRepository.existsByName(team.getName())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        
        return teamRepository.save(team);
    }

    @Override
    public Optional<Team> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return teamRepository.findById(id);
    }

    @Override
    public Optional<Team> findByName(String name) {
        return teamRepository.findByName(name);
    }

    @Override
    public PageResponse<Team> findAll(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<Team> teamPage;
        
        if (StringUtils.hasText(keyword)) {
            teamPage = teamRepository.findAll(
                (root, query, cb) -> cb.like(root.get("name"), "%" + keyword + "%"),
                pageable
            );
        } else {
            teamPage = teamRepository.findAll(pageable);
        }
        
        return PageResponse.<Team>builder()
                .content(teamPage.getContent())
                .totalElements(teamPage.getTotalElements())
                .totalPages(teamPage.getTotalPages())
                .currentPage(teamPage.getNumber())
                .pageSize(teamPage.getSize())
                .build();
    }

    @Override
    public List<Team> findAll() {
        return teamRepository.findAll();
    }

    @Override
    @Transactional
    public Team updateTeam(UUID id, Team team) {
        log.info("更新团队: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Team existingTeam = teamRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        if (team.getName() != null) {
            existingTeam.setName(team.getName());
        }
        if (team.getDescription() != null) {
            existingTeam.setDescription(team.getDescription());
        }
        if (team.getParentId() != null) {
            existingTeam.setParentId(team.getParentId());
        }
        
        return teamRepository.save(Objects.requireNonNull(existingTeam));
    }

    @Override
    @Transactional
    public void deleteTeam(UUID id) {
        log.info("删除团队: id={}", id);
        if (id != null) {
            teamRepository.deleteById(id);
        }
    }

    @Override
    public boolean existsByName(String name) {
        return teamRepository.existsByName(name);
    }

    @Override
    public List<Team> findByParentId(UUID parentId) {
        if (parentId == null) {
            return findRootTeams();
        }
        return teamRepository.findByParentId(parentId);
    }

    @Override
    public List<Team> findRootTeams() {
        return teamRepository.findByParentIdIsNull();
    }

    @Override
    public List<Team> findTeamTree(UUID teamId) {
        List<Team> result = new ArrayList<>();
        Team team = teamRepository.findById(Objects.requireNonNull(teamId)).orElse(null);
        if (team != null) {
            UUID teamIdValue = team.getId();
            if (teamIdValue != null) {
                result.add(team);
                addChildTeams(Objects.requireNonNull(teamIdValue), result);
            }
        }
        return result;
    }

    private void addChildTeams(UUID parentId, List<Team> result) {
        List<Team> children = teamRepository.findByParentId(parentId);
        for (Team child : children) {
            result.add(child);
            UUID childId = child.getId();
            if (childId != null) {
                addChildTeams(Objects.requireNonNull(childId), result);
            }
        }
    }

    @Override
    @Transactional
    public void addMember(UUID teamId, UUID userId, String role) {
        log.info("添加团队成员: teamId={}, userId={}, role={}", teamId, userId, role);
        
        if (!teamRepository.existsById(Objects.requireNonNull(teamId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!userRepository.existsById(Objects.requireNonNull(userId))) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        
        TeamMember member = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .isLeader("leader".equals(role))
                .build();
        teamMemberRepository.save(Objects.requireNonNull(member));
    }

    @Override
    @Transactional
    public void removeMember(UUID teamId, UUID userId) {
        log.info("移除团队成员: teamId={}, userId={}", teamId, userId);
        teamMemberRepository.deleteByTeamIdAndUserId(teamId, userId);
    }

    @Override
    public List<UUID> getMemberIds(UUID teamId) {
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toList());
    }

    @Override
    public List<UUID> getTeamUserIds(UUID teamId) {
        List<UUID> userIds = new ArrayList<>();
        userIds.addAll(getMemberIds(teamId));
        for (Team child : findByParentId(teamId)) {
            userIds.addAll(getTeamUserIds(child.getId()));
        }
        return userIds;
    }

    @Override
    public boolean isMember(UUID teamId, UUID userId) {
        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    @Override
    public boolean isLeader(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(member -> Boolean.TRUE.equals(member.getIsLeader()))
                .orElse(false);
    }
}
