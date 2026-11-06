import React from 'react';
import { Tooltip } from 'antd';
import { useT } from '@/i18n';
import OwlMark from '@/components/brand/OwlMark';

/**
 * 侧边栏品牌区（t89 建立 / t108 换徽标与更名 / t122 两行左对齐 + 徽标再放大 15%）。
 *
 * - 徽标：`OwlMark` 单色版（`currentColor`），随深色 Sider 自适应；
 *   展开 **37px**（t118 的 32 → +15%）、折叠 **28px**（26 → +8%，80px 栏宽仍绰绰有余）；
 * - 展开：图标在左，右侧**两行左对齐** ——
 *   第一行 `app.brand.line1`（猫头鹰漏洞审计，600 字重），
 *   第二行 `app.brand.line2`（生命周期管理系统，字号小 2–3px、字重更轻、对比度略低）；
 * - 折叠：只显示徽标，hover Tooltip 用 `app.brand.nameShort`（「猫头鹰审计」），读屏靠 `aria-label`；
 * - 行高与内边距收紧（`padding: 8px 14px`、`minHeight: 60`），两行总高不超过 56px 的旧占位，
 *   不挤压下方菜单首项；
 * - 名称全部走 i18n，中英各一份；英文更长时 `-webkit-line-clamp: 1` + 省略号兜底。
 */
export interface AppBrandProps {
  collapsed?: boolean;
}

const AppBrand: React.FC<AppBrandProps> = ({ collapsed = false }) => {
  const { t } = useT();
  const name = t('app.brand.name');
  const shortName = t('app.brand.nameShort');
  const line1 = t('app.brand.line1');
  const line2 = t('app.brand.line2');

  /** 两行共用的省略兜底：最多一行，超出打点，绝不撑破 200px 侧栏 */
  const lineBase: React.CSSProperties = {
    display: 'block',
    maxWidth: '100%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    letterSpacing: 0.2,
  };

  const line1Style: React.CSSProperties = {
    ...lineBase,
    fontSize: 13,
    fontWeight: 650,
    lineHeight: 1.25,
    color: '#fff',
  };

  const line2Style: React.CSSProperties = {
    ...lineBase,
    fontSize: 10.5,
    fontWeight: 400,
    lineHeight: 1.35,
    color: 'rgba(255, 255, 255, 0.66)',
    marginTop: 1,
  };

  const content = (
    <div
      data-testid="app-brand"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: collapsed ? 'center' : 'flex-start',
        gap: collapsed ? 0 : 10,
        minHeight: 60,
        height: collapsed ? 60 : 'auto',
        padding: collapsed ? '0 8px' : '8px 14px',
        color: '#fff',
        overflow: 'hidden',
        borderBottom: '1px solid rgba(255, 255, 255, 0.12)',
        marginBottom: 4,
        boxSizing: 'border-box',
      }}
    >
      <OwlMark size={collapsed ? 28 : 37} variant="monochrome" />
      {collapsed ? null : (
        <span
          data-testid="app-brand-name"
          title={name}
          style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', flex: '1 1 auto', minWidth: 0 }}
        >
          <span data-testid="app-brand-line1" style={line1Style}>
            {line1}
          </span>
          <span data-testid="app-brand-line2" style={line2Style}>
            {line2}
          </span>
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
