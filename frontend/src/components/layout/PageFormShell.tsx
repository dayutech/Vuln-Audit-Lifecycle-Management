import React from 'react';
import { Button, Card } from 'antd';
import { useT } from '@/i18n';
import './page.css';

/**
 * 表单页统一外壳（t142）：`分组卡片 + 吸底操作条`。
 *
 * 客户/项目的新建、编辑页共用它；后续资产/漏洞/任务/知识库/文档的表单页直接复用，
 * 保证「间距、标题层级、按钮位置（右下）、取消=返回上一页」完全一致。
 *
 * - 分组：用 `FormGroup`（薄包装 antd Card）把字段按语义分块，避免一屏几十个输入框；
 * - 操作条：`position: sticky; bottom: 0`，长表单滚动时按钮始终可见；窄屏纵向排列并撑满。
 */
export interface PageFormShellProps {
  children: React.ReactNode;
  /** 保存按钮 loading */
  submitting?: boolean;
  /** 保存 */
  onSubmit: () => void;
  /** 取消（默认= 返回上一页，由调用方传入） */
  onCancel: () => void;
  submitText?: string;
  cancelText?: string;
  /** 操作条左侧的提示文案（如「编码创建后不可修改」） */
  hint?: React.ReactNode;
  /** 禁用保存按钮（如：无写权限） */
  submitDisabled?: boolean;
  /** 测试用前缀，默认 `page-form` */
  testId?: string;
}

export const PageFormShell: React.FC<PageFormShellProps> = ({
  children,
  submitting,
  onSubmit,
  onCancel,
  submitText,
  cancelText,
  hint,
  submitDisabled,
  testId = 'page-form',
}) => {
  const { t } = useT();
  return (
    <div className="page" data-testid={testId}>
      {children}
      <div className="page-form__actions" data-testid={`${testId}-actions`}>
        {hint ? <span className="page-form__hint">{hint}</span> : null}
        <Button onClick={onCancel} data-testid={`${testId}-cancel`}>
          {cancelText ?? t('common.cancel')}
        </Button>
        <Button
          type="primary"
          loading={submitting}
          disabled={submitDisabled}
          onClick={onSubmit}
          data-testid={`${testId}-submit`}
        >
          {submitText ?? t('common.save')}
        </Button>
      </div>
    </div>
  );
};

/** 表单分组卡片（薄包装，统一圆角/标题字号） */
export interface FormGroupProps {
  title: React.ReactNode;
  children: React.ReactNode;
  extra?: React.ReactNode;
}

export const FormGroup: React.FC<FormGroupProps> = ({ title, children, extra }) => (
  <Card className="page-group" title={title} extra={extra} size="small">
    {children}
  </Card>
);

export default PageFormShell;
