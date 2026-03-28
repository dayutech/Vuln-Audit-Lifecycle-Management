import type { PaginatedResponse } from './common';
import type { UserVO } from './user';

export interface ToolVO {
  id: string;
  name: string;
  category: string;
  description?: string;
  requirements?: string;
  status: string;
  created_by?: UserVO;
  created_at: string;
}

export type ToolListResponse = PaginatedResponse<ToolVO>;
