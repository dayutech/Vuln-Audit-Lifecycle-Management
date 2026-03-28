import request from '@/utils/request';
import type { TeamVO, CreateTeamRequest, AddTeamMemberRequest } from '@/types/team';
import { useUserStore } from '@/store/userStore';

export const getTeamList = (): Promise<any> => {
  return request.get('/teams');
};

export const getTeamDetail = (id: string): Promise<TeamVO> => {
  return request.get(`/teams/${id}`);
};

export const createTeam = (data: CreateTeamRequest): Promise<TeamVO> => {
  const currentUserId = useUserStore.getState().user?.id;
  return request.post('/teams', {
    name: data.name,
    description: data.description,
    leaderId: data.leader_id ?? currentUserId,
    createdBy: currentUserId,
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

export const deleteTeam = (id: string): Promise<any> => {
  return request.delete(`/teams/${id}`);
};
