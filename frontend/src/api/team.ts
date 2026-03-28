import request from '@/utils/request';
import type { TeamVO, CreateTeamRequest, AddTeamMemberRequest } from '@/types/team';

export const getTeamList = (): Promise<any> => {
  return request.get('/teams');
};

export const getTeamDetail = (id: string): Promise<TeamVO> => {
  return request.get(`/teams/${id}`);
};

export const createTeam = (data: CreateTeamRequest): Promise<TeamVO> => {
  return request.post('/teams', data);
};

export const addTeamMember = (id: string, data: AddTeamMemberRequest): Promise<any> => {
  return request.post(`/teams/${id}/members`, data);
};

export const deleteTeam = (id: string): Promise<any> => {
  return request.delete(`/teams/${id}`);
};
