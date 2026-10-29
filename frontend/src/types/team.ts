import type { PaginatedResponse } from './common';

export interface TeamMemberVO {
  id: string;
  username: string;
  full_name: string;
  role: string;
  status: string;
}

/**
 * 团队（后端 /teams 直接返回 Team 实体：camelCase + parentId/topLevel）。
 * leader/members/member_count 为历史前端假设字段，接口并未返回，仅做可选兼容。
 */
export interface TeamVO {
  id: string;
  name: string;
  description?: string;
  parentId?: string | null;
  parent_id?: string | null;
  leaderId?: string | null;
  leader_id?: string | null;
  createdBy?: string | null;
  topLevel?: boolean;
  leader?: {
    id: string;
    username: string;
    full_name: string;
  };
  members?: TeamMemberVO[];
  member_count?: number;
  assets_count?: number;
  created_at: string;
  createdAt?: string;
  updatedAt?: string;
}

export type TeamListResponse = PaginatedResponse<TeamVO>;

export interface CreateTeamRequest {
  name: string;
  description?: string;
  parent_id?: string;
  leader_id?: string;
}

/** 团队成员视图（由 /teams/{id}/members 的用户 ID + /users 解析而来） */
export interface TeamMemberView {
  userId: string;
  username: string;
  fullName: string;
  isLeader: boolean;
}

export interface AddTeamMemberRequest {
  user_id: string;
  role: string;
}
