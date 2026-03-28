import request from '@/utils/request';

export const getAssetStats = (): Promise<any> => {
  return request.get('/dashboard/assets');
};

export const getVulnerabilityStats = (): Promise<any> => {
  return request.get('/dashboard/vulnerabilities');
};

export const getWorkloadStats = (): Promise<any> => {
  return request.get('/dashboard/workload');
};
