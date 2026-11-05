import React from 'react';

/**
 * 第三方登录的简笔图标（t106）。
 *
 * **不引入图标库、不引入品牌资源文件**：四家各用一个内联 SVG 的极简几何示意
 * （GitHub 猫头 / Microsoft 四方块 / Google 圆环缺口 / WeChat 双气泡），
 * 全部 `currentColor`，颜色跟随按钮文字（因此自动适配 8 套登录皮肤与深浅底色）。
 */
export interface SsoIconProps {
  code: string;
  size?: number;
  className?: string;
}

const normalize = (code: string): string => (code || '').trim().toLowerCase();

const GitHubGlyph: React.FC = () => (
  <>
    {/* 猫头：圆脸 + 双耳 + 尾巴 */}
    <path
      d="M7.6 4.4 8.9 7h6.2l1.3-2.6 1.4 3.2c1 1 1.6 2.4 1.6 3.9 0 4-3.4 6.2-7.4 6.2S4.6 15.5 4.6 11.5c0-1.5.6-2.9 1.6-3.9L7.6 4.4Z"
      fill="currentColor"
    />
    <circle cx="9.6" cy="11.4" r="1.05" fill="#fff" opacity="0.9" />
    <circle cx="14.4" cy="11.4" r="1.05" fill="#fff" opacity="0.9" />
    <path d="M9.4 16.5c.8.5 1.6.7 2.6.7s1.8-.2 2.6-.7" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" fill="none" />
  </>
);

const MicrosoftGlyph: React.FC = () => (
  <>
    <rect x="4" y="4" width="7" height="7" fill="currentColor" />
    <rect x="13" y="4" width="7" height="7" fill="currentColor" opacity="0.78" />
    <rect x="4" y="13" width="7" height="7" fill="currentColor" opacity="0.78" />
    <rect x="13" y="13" width="7" height="7" fill="currentColor" opacity="0.56" />
  </>
);

const GoogleGlyph: React.FC = () => (
  <>
    {/* 缺口圆环 + 横杠 = 极简 G */}
    <path
      d="M19 12.2a7 7 0 1 1-2.2-5.1"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
    />
    <path d="M12.4 12.2h6.4" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
  </>
);

const WechatGlyph: React.FC = () => (
  <>
    {/* 双气泡 */}
    <path
      d="M10.2 5.4c3.3 0 6 2.2 6 5s-2.7 5-6 5c-.7 0-1.4-.1-2-.3L5.4 16l.8-2.3C5 12.7 4.2 11.5 4.2 10.4c0-2.8 2.7-5 6-5Z"
      fill="currentColor"
    />
    <path
      d="M15.6 12.1c2.4 0 4.2 1.6 4.2 3.5 0 .8-.5 1.6-1.2 2.2l.6 1.7-2.2-.9c-.4.1-.9.2-1.4.2-2.4 0-4.2-1.6-4.2-3.5"
      fill="currentColor"
      opacity="0.62"
    />
  </>
);

const GenericGlyph: React.FC = () => (
  <path
    d="M10 14a4 4 0 0 1 0-5.7l1.4-1.4a4 4 0 0 1 5.7 5.7L16 13.6M14 10a4 4 0 0 1 0 5.7l-1.4 1.4A4 4 0 0 1 6.9 11.4L8 10.3"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.9"
    strokeLinecap="round"
  />
);

const SsoIcon: React.FC<SsoIconProps> = ({ code, size = 18, className }) => {
  const key = normalize(code);
  const glyph =
    key === 'github' ? (
      <GitHubGlyph />
    ) : key === 'microsoft' ? (
      <MicrosoftGlyph />
    ) : key === 'google' ? (
      <GoogleGlyph />
    ) : key === 'wechat' ? (
      <WechatGlyph />
    ) : (
      <GenericGlyph />
    );

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      className={className}
      aria-hidden="true"
      focusable="false"
      data-sso-icon={key}
      style={{ display: 'block', flex: '0 0 auto' }}
    >
      {glyph}
    </svg>
  );
};

export default SsoIcon;
