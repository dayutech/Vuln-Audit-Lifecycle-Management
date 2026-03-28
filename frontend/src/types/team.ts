import type { PaginatedResponse } from './common';

export interface TeamMemberVO {
  id: string;
  username: string;
  full_name: string;
  role: string;
  status: string;
}

export interface TeamVO {
  id: string;
  name: string;
  description?: string;
  leader?: {
    id: string;
    username: string;
    full_name: string;
  };
  members?: TeamMemberVO[];
  member_count?: number;
  assets_count?: number;
  created_at: string;
}

export type TeamListResponse = PaginatedResponse<TeamVO>;

export interface CreateTeamRequest {
  name: string;
  description?: string;
  leader_id?: string;
}

export interface AddTeamMemberRequest {
  user_id: string;
  role: string;
}
