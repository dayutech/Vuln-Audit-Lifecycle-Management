import React from 'react';
import { Button, Space, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useT } from '@/i18n';
import { useBackNavigation } from '@/hooks/useBackNavigation';
import './page.css';

/**
 * 详情 / 编辑 / 新建页统一页头（t142）。
 *
 * 结构：`[← 返回] 标题 + 副标题（编码等）      右侧操作区`
 *
 * - 返回：默认用 `useBackNavigation(fallbackTo)`（有站内历史 → `navigate(-1)`；
 *   否则 `replace` 到列表页，避免新标签页打开时跳出站点）；
 *   业务方若需要自定义（例如表单有未保存改动），可传 `onBack` 覆盖。
 * - 该组件不带任何业务逻辑，资产/漏洞/任务/知识库/文档后续照此复用即可。
 */
export interface PageHeaderProps {
  /** 主标题（客户名 / 项目名 / 「新建客户」） */
  title: React.ReactNode;
  /** 副标题：编码、状态说明等 */
  subtitle?: React.ReactNode;
  /** 无站内历史时的兜底路由（列表页），例如 `/customers` */
  fallbackTo: string;
  /** 覆盖默认返回行为（如：有未保存改动时二次确认） */
  onBack?: () => void;
  /** 右侧操作区（按钮组），窄屏会换行 */
  extra?: React.ReactNode;
  /** 返回按钮文案，默认「返回」 */
  backText?: string;
  /** 测试用前缀，默认 `page-header` */
  testId?: string;
}

const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  subtitle,
  fallbackTo,
  onBack,
  extra,
  backText,
  testId = 'page-header',
}) => {
  const { t } = useT();
  const { goBack } = useBackNavigation(fallbackTo);

  return (
    <div className="page-header" data-testid={testId}>
      <div className="page-header__left">
        <Button
          className="page-header__back"
          icon={<ArrowLeftOutlined />}
          onClick={onBack ?? goBack}
          data-testid="page-header-back"
        >
          {backText ?? t('common.back')}
        </Button>
        <div className="page-header__titles">
          <Typography.Title level={3} className="page-header__title" data-testid="page-header-title">
            {title}
          </Typography.Title>
          {subtitle ? (
            <p className="page-header__subtitle" data-testid="page-header-subtitle">
              {subtitle}
            </p>
          ) : null}
        </div>
      </div>
      {extra ? (
        <Space className="page-header__extra" data-testid="page-header-extra" wrap>
          {extra}
        </Space>
      ) : null}
    </div>
  );
};

export default PageHeader;
