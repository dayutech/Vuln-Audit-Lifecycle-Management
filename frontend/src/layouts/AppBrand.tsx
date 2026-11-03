import React from 'react';
import { Tooltip } from 'antd';
import { useT } from '@/i18n';

/**
 * 侧边栏品牌区（t89）：Logo + 产品名。
 *
 * - 展开：图标在左、产品名在右；
 * - 折叠：只显示图标，hover 用 Tooltip 显示产品名（Sider 宽度 80px，名称放不下）；
 * - Logo 用内联 SVG（不引入新依赖），`fill="currentColor"` 以便在深色 Sider 上自适应；
 * - 名称走 i18n（`app.brand.name`），随语言切换。
 */
export interface AppBrandProps {
  collapsed?: boolean;
}

const BrandLogo: React.FC<{ size?: number }> = ({ size = 26 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 32 32"
    role="img"
    aria-hidden="true"
    focusable="false"
    style={{ display: 'block', flex: '0 0 auto' }}
  >
    {/* 盾牌：漏洞审计（防护 + 检查） */}
    <path
      d="M16 2.5 27 6.6v9.1c0 6.3-4.3 11.6-11 13.8-6.7-2.2-11-7.5-11-13.8V6.6L16 2.5Z"
      fill="currentColor"
      opacity="0.18"
    />
    <path
      d="M16 2.5 27 6.6v9.1c0 6.3-4.3 11.6-11 13.8-6.7-2.2-11-7.5-11-13.8V6.6L16 2.5Z"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinejoin="round"
    />
    {/* 放大镜：审计 / 排查 */}
    <circle cx="14" cy="14.5" r="4.1" fill="none" stroke="currentColor" strokeWidth="1.7" />
    <path d="M17.2 17.9 21 21.7" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
  </svg>
);

const AppBrand: React.FC<AppBrandProps> = ({ collapsed = false }) => {
  const { t } = useT();
  const name = t('app.brand.name');

  const content = (
    <div
      data-testid="app-brand"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: collapsed ? 'center' : 'flex-start',
        gap: 10,
        height: 56,
        padding: collapsed ? '0 8px' : '0 16px',
        color: '#fff',
        overflow: 'hidden',
        whiteSpace: 'nowrap',
        borderBottom: '1px solid rgba(255, 255, 255, 0.12)',
        marginBottom: 4,
      }}
    >
      <BrandLogo size={collapsed ? 24 : 26} />
      {collapsed ? null : (
        <span
          data-testid="app-brand-name"
          style={{ fontSize: 15, fontWeight: 600, letterSpacing: 0.3, overflow: 'hidden', textOverflow: 'ellipsis' }}
        >
          {name}
        </span>
      )}
    </div>
  );

  // 折叠时名称不可见 → 用 Tooltip 兜住（鼠标 hover 可读，读屏由 aria-label 兜底）
  return collapsed ? (
    <Tooltip title={name} placement="right" mouseEnterDelay={0.2}>
      <div aria-label={name} style={{ cursor: 'pointer' }}>
        {content}
      </div>
    </Tooltip>
  ) : (
    content
  );
};

export default AppBrand;
