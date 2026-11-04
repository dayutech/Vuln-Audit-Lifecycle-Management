import React from 'react';
import { characterClassName, useLoginCharacterEffects } from '../characterEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `geometric`：圆头方形身（浅蓝 / 深蓝 / 白）。
 * 焦点进密码框时**把头撇向一侧**、眼睛整体偏移（露出「我不看」的姿态）。
 */
const GeometricCharacter: React.FC<LoginCharacterProps> = ({
  passwordFocused,
  hasError = false,
  size,
  className,
}) => {
  const { svgRef, pupil, blinking, shaking } = useLoginCharacterEffects(hasError);
  const limit = 5;

  return (
    <div className={['login-char', className].filter(Boolean).join(' ')}>
      <svg
        ref={svgRef}
        data-testid="login-char-svg"
        data-theme="geometric"
        className={characterClassName('theme-geometric', { blinking, shaking, shielding: passwordFocused })}
        viewBox="0 0 200 200"
        style={size ? { maxWidth: size } : undefined}
        aria-hidden="true"
        focusable="false"
      >
        <g className="char-bob">
          {/* 方形身体 */}
          <rect x="66" y="124" width="68" height="52" rx="14" fill="#2b59c3" />
          <rect x="80" y="138" width="12" height="12" rx="4" fill="#dcecff" opacity="0.85" />
          <rect x="108" y="138" width="12" height="12" rx="4" fill="#dcecff" opacity="0.85" />

          {/* 圆头（可与身体分离旋转）*/}
          <g className="geo-head">
            <circle cx="100" cy="86" r="46" fill="#dcecff" stroke="#2b59c3" strokeWidth="4" />
            {/* 眼睛底座 */}
            <circle cx="83" cy="82" r="15" fill="#fff" stroke="#2b59c3" strokeWidth="3.4" />
            <circle cx="117" cy="82" r="15" fill="#fff" stroke="#2b59c3" strokeWidth="3.4" />
            <g
              className="char-pupil geo-eye-group"
              data-testid="login-char-pupil"
              style={{ transform: `translate(${(pupil.x * limit).toFixed(2)}px, ${(pupil.y * limit).toFixed(2)}px)` }}
            >
              <circle cx="83" cy="82" r="6.4" fill="#2b59c3" />
              <circle cx="117" cy="82" r="6.4" fill="#2b59c3" />
            </g>
            {/* 眼睑 */}
            <g className="geo-lid">
              <rect x="66" y="65" width="34" height="34" rx="12" fill="#dcecff" />
              <rect x="100" y="65" width="34" height="34" rx="12" fill="#dcecff" />
            </g>
            {/* 嘴 */}
            <rect x="92" y="108" width="16" height="5" rx="2.5" fill="#2b59c3" />
          </g>
        </g>
      </svg>
    </div>
  );
};

export default GeometricCharacter;
