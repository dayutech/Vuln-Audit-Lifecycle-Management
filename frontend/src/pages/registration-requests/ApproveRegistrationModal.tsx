import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Checkbox, Modal, Spin, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import {
  approveRegistration,
  getApiErrorMessage,
  isAssignableRole,
  maskEmail,
  maskPhone,
  type RegistrationRequestVO,
} from '@/api/registrationReview';
import { getRoleList } from '@/api/role';
import type { RoleVO } from '@/types/role';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 审批通过弹窗（t114）。
 *
 * 关键约束（对应 t111 的后端口径）：
 * - **角色默认空**（`useState<string[]>([])`），不预选、不隐式补 `vulnerability_engineer`；
 * - 空数组提交 = 建号但不授角色（登录后仅默认 ROLE_USER），弹窗内用醒目提示说明后果；
 * - 角色列表来自 `GET /roles`，按 `isAssignableRole` 收敛：非 ADMIN 审批人看不到 `admin`（防自助提权），
 *   后端若返回 `assignable === false` 一并排除；
 * - 失败 400/403/409/5xx 分级：400 原样透出后端「角色不存在: xxx」，409 提示已被处理并让列表刷新。
 */
export interface ApproveRegistrationModalProps {
  request: RegistrationRequestVO | null;
  viewerIsAdmin: boolean;
  onClose: () => void;
  /** 审批成功（页面据此刷新列表 + 刷新角标） */
  onSucceeded: (payload: { username: string; roleCodes: string[] }) => void;
}

const ApproveRegistrationModal: React.FC<ApproveRegistrationModalProps> = ({
  request,
  viewerIsAdmin,
  onClose,
  onSucceeded,
}) => {
  const { t } = useT();
  /** 默认空：这是"禁止前端偷偷塞默认角色"的实现点 */
  const [roleCodes, setRoleCodes] = useState<string[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [rolesFailed, setRolesFailed] = useState(false);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    setRoleCodes([]);
    setRolesFailed(false);
    setLoadingRoles(true);
    getRoleList()
      .then((res: any) => {
        if (cancelled) return;
        const payload = res?.data ?? res;
        const list: RoleVO[] = Array.isArray(payload) ? payload : Array.isArray(payload?.content) ? payload.content : [];
        setRoles(list);
        if (list.length === 0) setRolesFailed(true);
      })
      .catch(() => {
        if (!cancelled) setRolesFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoadingRoles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [request]);

  const options = useMemo(
    () =>
      roles
        .filter((role) => isAssignableRole({ code: role.code, isSystem: role.isSystem }, viewerIsAdmin))
        .map((role) => ({
          value: role.code,
          label: role.name ? `${role.name}（${role.code}）` : role.code,
          system: role.isSystem,
        })),
    [roles, viewerIsAdmin],
  );

  const submit = async () => {
    if (!request || submitting) return;
    setSubmitting(true);
    try {
      const result = await approveRegistration(request.id, roleCodes);
      if (roleCodes.length > 0) {
        message.success(t('registrationReview.approve.successWithRoles', { username: result.username || request.username, count: roleCodes.length }));
      } else {
        message.success(t('registrationReview.approve.successNoRoles', { username: result.username || request.username }));
      }
      onSucceeded({ username: result.username || request.username, roleCodes });
    } catch (error) {
      const status = getApiErrorStatus(error);
      const backend = getApiErrorMessage(error);
      if (status === 400) message.error(t('registrationReview.approve.err400', { message: backend }));
      else if (status === 403) message.error(t('registrationReview.approve.err403'));
      else if (status === 409) message.warning(t('registrationReview.approve.err409'));
      else if (typeof status === 'number' && status >= 500) message.error(t('registrationReview.approve.err5xx', { status }));
      else message.error(t('registrationReview.approve.errGeneric', { message: backend }));
    } finally {
      setSubmitting(false);
    }
  };

  if (!request) return null;

  return (
    <Modal
      open
      title={t('registrationReview.approve.title')}
      onCancel={submitting ? undefined : onClose}
      onOk={submit}
      okText={t('registrationReview.approve.submit')}
      cancelText={t('preferences.close')}
      confirmLoading={submitting}
      okButtonProps={{ disabled: rolesFailed, 'data-testid': 'registration-approve-submit' }}
      cancelButtonProps={{ 'data-testid': 'registration-approve-cancel' }}
      destroyOnHidden
    >
      <div data-testid="registration-approve-modal">
        <div style={{ fontSize: 13, fontWeight: 600, margin: '4px 0 8px' }}>
          {t('registrationReview.approve.detailTitle')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '6px 12px', fontSize: 13 }}>
          <span style={{ color: '#6b7280' }}>{t('registrationReview.approve.field.username')}</span>
          <span data-testid="registration-approve-username">{request.username}</span>
          <span style={{ color: '#6b7280' }}>{t('registrationReview.approve.field.phone')}</span>
          <span>
            <span data-testid="registration-approve-phone">{maskPhone(request.phone)}</span>{' '}
            <Tag color={request.phoneVerified ? 'green' : 'default'} data-testid="registration-approve-phone-verified">
              {request.phoneVerified ? t('registrationReview.approve.verified') : t('registrationReview.approve.unverified')}
            </Tag>
          </span>
          <span style={{ color: '#6b7280' }}>{t('registrationReview.approve.field.email')}</span>
          <span>
            {request.email ? (
              <>
                <span data-testid="registration-approve-email">{maskEmail(request.email)}</span>{' '}
                <Tag color={request.emailVerified ? 'green' : 'default'} data-testid="registration-approve-email-verified">
                  {request.emailVerified ? t('registrationReview.approve.verified') : t('registrationReview.approve.unverified')}
                </Tag>
              </>
            ) : (
              <span style={{ color: '#9ca3af' }}>{t('registrationReview.emptyPlaceholder')}</span>
            )}
          </span>
          <span style={{ color: '#6b7280' }}>{t('registrationReview.approve.field.createdAt')}</span>
          <span data-testid="registration-approve-created-at">
            {request.createdAt ? dayjs(request.createdAt).format('YYYY-MM-DD HH:mm:ss') : t('registrationReview.emptyPlaceholder')}
          </span>
        </div>

        <div style={{ fontSize: 13, fontWeight: 600, margin: '14px 0 8px' }}>{t('registrationReview.approve.rolesLabel')}</div>
        {rolesFailed ? (
          <Alert
            type="warning"
            showIcon
            message={t('registrationReview.approve.rolesLoadFailed')}
            data-testid="registration-approve-roles-failed"
          />
        ) : (
          /**
           * 角色用**勾选列表**而不是多选下拉：
           * - 任务口径就是「角色勾选」，勾选清单对少量固定角色更直观、也不会有下拉虚拟化问题；
           * - 每个角色一个 check 框（`data-testid=registration-role-<code>`），E2E 可直接按 testid 点选；
           * - 默认一个都不勾（`roleCodes` 初始为空数组），不预选、不隐式补角色。
           */
          <div data-testid="registration-approve-roles">
            {loadingRoles ? (
              <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 6 }} data-testid="registration-approve-roles-loading">
                <Spin size="small" /> <span>{t('common.loading')}</span>
              </div>
            ) : null}
            <Checkbox.Group
              value={roleCodes}
              onChange={(value) => setRoleCodes(value as string[])}
              style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
            >
              {options.map((option) => (
                <Checkbox key={option.value} value={option.value} data-testid={`registration-role-${option.value}`}>
                  <span style={{ fontSize: 13 }}>
                    {option.label}{' '}
                    {option.system ? <Tag style={{ marginLeft: 6 }}>{t('registrationReview.approve.rolesSystemTag')}</Tag> : null}
                  </span>
                </Checkbox>
              ))}
            </Checkbox.Group>
          </div>
        )}

        <Alert
          type={roleCodes.length === 0 ? 'warning' : 'info'}
          showIcon
          style={{ marginTop: 12 }}
          message={t('registrationReview.approve.rolesEmptyHint')}
          data-testid="registration-approve-roles-hint"
        />

        <Typography.Paragraph style={{ marginTop: 10, marginBottom: 0, fontSize: 12, color: '#6b7280' }}>
          {t('registrationReview.subtitle')}
        </Typography.Paragraph>
      </div>
    </Modal>
  );
};

export default ApproveRegistrationModal;


