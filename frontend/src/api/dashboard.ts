import request from '@/utils/request';

export const getAssetStats = (): Promise<any> => {
  return request.get('/dashboard/assets/stats');
};

export const getVulnerabilityStats = (): Promise<any> => {
  return request.get('/dashboard/vulnerabilities/stats');
};

export const getWorkloadStats = (): Promise<any> => {
  return request.get('/dashboard/workload/stats');
};
