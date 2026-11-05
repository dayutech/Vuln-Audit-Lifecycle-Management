import React from 'react';
import { Tooltip } from 'antd';
import { useT } from '@/i18n';
import OwlMark from '@/components/brand/OwlMark';

/**
 * 侧边栏品牌区（t89 建立 / t108 换徽标与更名）。
 *
 * - 徽标：`OwlMark` 单色版（`currentColor`），随深色 Sider 自适应，不再有自绘色块底板；
 * - 展开：图标在左、全名在右。中文全名 15 字，在 200px Sider 里**两行**排完
 *   （`-webkit-line-clamp: 2` + `text-overflow: ellipsis` 兜底，英文过长时优雅省略，绝不撑破布局）；
 * - 折叠：只显示徽标，hover 用 Tooltip 显示 `app.brand.nameShort`（「猫头鹰审计」），
 *   读屏由 `aria-label` 兜底；
 * - 名称全部走 i18n（`app.brand.name` / `app.brand.nameShort`），随语言切换。
 */
export interface AppBrandProps {
  collapsed?: boolean;
}

const AppBrand: React.FC<AppBrandProps> = ({ collapsed = false }) => {
  const { t } = useT();
  const name = t('app.brand.name');
  const shortName = t('app.brand.nameShort');

  const nameStyle: React.CSSProperties = {
    fontSize: 12,
    fontWeight: 600,
    lineHeight: 1.3,
    letterSpacing: 0.2,
    color: '#fff',
    whiteSpace: 'normal',
    wordBreak: 'break-word',
    display: '-webkit-box',
    WebkitLineClamp: 2,
    WebkitBoxOrient: 'vertical',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    flex: '1 1 auto',
    minWidth: 0,
  } as React.CSSProperties;

  const content = (
    <div
      data-testid="app-brand"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: collapsed ? 'center' : 'flex-start',
        gap: 9,
        minHeight: 56,
        height: collapsed ? 56 : 'auto',
        padding: collapsed ? '0 8px' : '8px 14px',
        color: '#fff',
        overflow: 'hidden',
        borderBottom: '1px solid rgba(255, 255, 255, 0.12)',
        marginBottom: 4,
        boxSizing: 'border-box',
      }}
    >
      <OwlMark size={collapsed ? 26 : 32} variant="monochrome" />
      {collapsed ? null : (
        <span data-testid="app-brand-name" title={name} style={nameStyle}>
          {name}
        </span>
      )}
    </div>
  );

  // 折叠时名称不可见 → Tooltip 用**短名**兜住（长名在 80px 里放不下）
  return collapsed ? (
    <Tooltip title={shortName} placement="right" mouseEnterDelay={0.2}>
      <div aria-label={shortName} data-testid="app-brand-collapsed" style={{ cursor: 'pointer' }}>
        {content}
      </div>
    </Tooltip>
  ) : (
    content
  );
};

export default AppBrand;

