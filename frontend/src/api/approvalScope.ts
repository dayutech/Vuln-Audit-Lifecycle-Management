import request from '@/utils/request';

/** 审批范围类型：team / asset / asset_category */
export type ApprovalScopeType = 'team' | 'asset' | 'asset_category';

export interface ApprovalScopeVO {
  id: string;
  approverId: string;
  scopeType: ApprovalScopeType | string;
  scopeId: string;
  grantedBy?: string | null;
  grantedAt?: string;
  expiresAt?: string | null;
  valid?: boolean;
}

export interface GrantApprovalScopeRequest {
  approverId: string;
  scopeType: ApprovalScopeType | string;
  scopeId: string;
  expiresAt?: string | null;
}

/** 查询审批范围（GET /approval-scopes?approverId=，管理员看全部，其他人只看自己的） */
export const listApprovalScopes = (approverId?: string): Promise<ApprovalScopeVO[]> => {
  return request.get('/approval-scopes', { params: { approverId } });
};

/** 我的审批范围（GET /approval-scopes/my） */
export const getMyApprovalScopes = (): Promise<ApprovalScopeVO[]> => {
  return request.get('/approval-scopes/my');
};

/** 授予审批范围（POST /approval-scopes，需 ADMIN / PROJECT_LEADER） */
export const grantApprovalScope = (data: GrantApprovalScopeRequest): Promise<ApprovalScopeVO> => {
  return request.post('/approval-scopes', {
    approverId: data.approverId,
    scopeType: data.scopeType,
    scopeId: data.scopeId,
    expiresAt: data.expiresAt ?? null,
  });
};

/** 撤销审批范围（DELETE /approval-scopes/{id}） */
export const revokeApprovalScope = (id: string): Promise<any> => {
  return request.delete(`/approval-scopes/${id}`);
};
