import request from '@/utils/request';
import type { TeamVO, CreateTeamRequest, AddTeamMemberRequest } from '@/types/team';
import { useUserStore } from '@/store/userStore';

export const getTeamList = (params?: { page?: number; page_size?: number; search?: string }): Promise<any> => {
  return request.get('/teams', {
    params: {
      page: Math.max((params?.page ?? 1) - 1, 0),
      size: params?.page_size ?? 100,
      keyword: params?.search,
    },
  });
};

/** 所有团队（不分页，用于构建层级树） */
export const getAllTeams = (): Promise<TeamVO[]> => {
  return request.get('/teams/all');
};

export const getRootTeams = (): Promise<TeamVO[]> => {
  return request.get('/teams/roots');
};

/** 指定团队及其所有子团队 */
export const getTeamTree = (id: string): Promise<TeamVO[]> => {
  return request.get(`/teams/${id}/tree`);
};

export const getTeamChildren = (id: string): Promise<TeamVO[]> => {
  return request.get(`/teams/${id}/children`);
};

export const getTeamDetail = (id: string): Promise<TeamVO> => {
  return request.get(`/teams/${id}`);
};

export const createTeam = (data: CreateTeamRequest): Promise<TeamVO> => {
  const currentUserId = useUserStore.getState().user?.id;
  return request.post('/teams', {
    name: data.name,
    description: data.description,
    parentId: data.parent_id || null,
    leaderId: data.leader_id ?? currentUserId,
    createdBy: currentUserId,
  });
};

export const updateTeam = (
  id: string,
  data: { name?: string; description?: string; parent_id?: string | null; leader_id?: string },
): Promise<TeamVO> => {
  return request.put(`/teams/${id}`, {
    name: data.name,
    description: data.description,
    parentId: data.parent_id ?? undefined,
    leaderId: data.leader_id,
  });
};

export const addTeamMember = (id: string, data: AddTeamMemberRequest): Promise<any> => {
  return request.post(`/teams/${id}/members`, null, {
    params: {
      userId: data.user_id,
      role: data.role,
    },
  });
};

/** 团队成员 ID 列表 */
export const getTeamMembers = (id: string): Promise<string[]> => {
  return request.get(`/teams/${id}/members`);
};

/** 团队及其子团队的所有用户 ID */
export const getTeamUsers = (id: string): Promise<string[]> => {
  return request.get(`/teams/${id}/users`);
};

export const removeTeamMember = (id: string, userId: string): Promise<any> => {
  return request.delete(`/teams/${id}/members/${userId}`);
};

export const deleteTeam = (id: string): Promise<any> => {
  return request.delete(`/teams/${id}`);
};
