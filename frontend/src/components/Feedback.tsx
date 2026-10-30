import React from 'react';
import { Button, Empty, Modal, Result, Spin } from 'antd';
import type { ModalFuncProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/i18n';

/**
 * i18n 化的通用反馈组件（t53 约定：空态 / 加载中 / 确认删除 / 403·404 页面）。
 *
 * 分页文案不在这里实现 —— antd `ConfigProvider locale` 已随语言切换
 * （见 src/i18n/index.tsx），Pagination 的「共 N 条 / 条每页」由 antd 自带词典；
 * 需要自定义时用 `useTotalText()` 拼 showTotal。
 */

/** 空态：默认文案走 i18n，可传覆盖说明 */
export const AppEmpty: React.FC<{ description?: React.ReactNode; children?: React.ReactNode }> = ({
  description,
  children,
}) => {
  const { t } = useT();
  return (
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description ?? t('common.empty')}>
      {children}
    </Empty>
  );
};

/** 加载中：Spin + 可选中英文案 */
export const AppLoading: React.FC<{ tip?: string }> = ({ tip }) => {
  const { t } = useT();
  return (
    <div style={{ textAlign: 'center', padding: '32px 0', color: 'rgba(0,0,0,0.45)' }}>
      <Spin size="large" />
      <div style={{ marginTop: 12, fontSize: 13 }}>{tip ?? t('common.loading')}</div>
    </div>
  );
};

/** 分页总数文案（配合 Table pagination.showTotal 使用） */
export const useTotalText = (): ((total: number) => string) => {
  const { t } = useT();
  return (total: number) => t('common.total', { count: total });
};

export interface ConfirmDeleteOptions
  extends Omit<ModalFuncProps, 'onOk' | 'okText' | 'cancelText' | 'content' | 'title'> {
  title?: string;
  content?: string;
  onOk: () => void | Promise<unknown>;
}

/**
 * 确认删除弹窗。
 *
 * 用 hook 形式是因为 `Modal.confirm` 渲染在 React 树之外，拿不到 Context；
 * hook 内先取当前语言的文案再调用 Modal.confirm，避免维护第二套语言状态。
 */
export const useConfirmDelete = (): ((options: ConfirmDeleteOptions) => void) => {
  const { t } = useT();
  return (options: ConfirmDeleteOptions) => {
    const { title, content, onOk, ...rest } = options;
    Modal.confirm({
      title: title ?? t('common.deleteConfirmTitle'),
      content: content ?? t('common.deleteConfirmContent'),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk,
      ...rest,
    });
  };
};

/** 401/403/404 页面：用于路由兜底与页面级无权限展示 */
export const ErrorPage: React.FC<{ status: '403' | '404'; subTitle?: string }> = ({ status, subTitle }) => {
  const { t } = useT();
  const navigate = useNavigate();
  const isForbidden = status === '403';
  return (
    <Result
      status={status}
      title={isForbidden ? t('error.forbiddenTitle') : t('error.notFoundTitle')}
      subTitle={subTitle ?? (isForbidden ? t('error.forbiddenDesc') : t('error.notFoundDesc'))}
      extra={
        <Button type="primary" onClick={() => navigate('/dashboard')}>
          {t('error.backHome')}
        </Button>
      }
    />
  );
};
