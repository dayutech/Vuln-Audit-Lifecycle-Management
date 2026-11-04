import React from 'react';
import { characterClassName, useLoginCharacterEffects } from '../characterEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `owl`：猫头鹰（芥末黄 / 深棕 / 米色）。
 * 焦点进密码框时**抬翅膀遮眼**。
 */
const OwlCharacter: React.FC<LoginCharacterProps> = ({
  passwordFocused,
  hasError = false,
  size,
  className,
}) => {
  const { svgRef, pupil, blinking, shaking } = useLoginCharacterEffects(hasError);
  const limit = 4;

  return (
    <div className={['login-char', className].filter(Boolean).join(' ')}>
      <svg
        ref={svgRef}
        data-testid="login-char-svg"
        data-theme="owl"
        className={characterClassName('theme-owl', { blinking, shaking, shielding: passwordFocused })}
        viewBox="0 0 200 200"
        style={size ? { maxWidth: size } : undefined}
        aria-hidden="true"
        focusable="false"
      >
        <g className="char-bob">
          {/* 身体 */}
          <ellipse cx="100" cy="122" rx="52" ry="50" fill="#e8b84b" stroke="#4a3728" strokeWidth="3.6" />
          {/* 腹部纹理 */}
          <path d="M82 132c6 6 30 6 36 0" fill="none" stroke="#4a3728" strokeWidth="2.6" strokeLinecap="round" opacity="0.7" />
          <path d="M84 146c6 5 26 5 32 0" fill="none" stroke="#4a3728" strokeWidth="2.6" strokeLinecap="round" opacity="0.5" />

          {/* 脚 */}
          <path d="M84 172l-6 8M84 172l0 9M84 172l6 8" stroke="#4a3728" strokeWidth="2.8" strokeLinecap="round" />
          <path d="M116 172l-6 8M116 172l0 9M116 172l6 8" stroke="#4a3728" strokeWidth="2.8" strokeLinecap="round" />

          {/* 眼睛盘 */}
          <circle cx="79" cy="94" r="26" fill="#f6ecd9" stroke="#4a3728" strokeWidth="3.2" />
          <circle cx="121" cy="94" r="26" fill="#f6ecd9" stroke="#4a3728" strokeWidth="3.2" />
          <g
            className="char-pupil"
            data-testid="login-char-pupil"
            style={{ transform: `translate(${(pupil.x * limit).toFixed(2)}px, ${(pupil.y * limit).toFixed(2)}px)` }}
          >
            <circle cx="79" cy="94" r="10" fill="#4a3728" />
            <circle cx="121" cy="94" r="10" fill="#4a3728" />
            <circle cx="82.4" cy="90.4" r="2.8" fill="#f6ecd9" />
            <circle cx="124.4" cy="90.4" r="2.8" fill="#f6ecd9" />
          </g>
          {/* 眼睑 */}
          <g className="owl-lid">
            <circle cx="79" cy="94" r="26" fill="#e8b84b" />
            <circle cx="121" cy="94" r="26" fill="#e8b84b" />
            <path d="M53 94h52M95 94h52" stroke="#4a3728" strokeWidth="2.6" strokeLinecap="round" />
          </g>

          {/* 喙 */}
          <path d="M100 104l8 12-8 6-8-6z" fill="#ff6b35" stroke="#4a3728" strokeWidth="2.4" strokeLinejoin="round" />

          {/* 左翅（常态） */}
          <path d="M50 118c-8 10-10 24-6 34" fill="#e8b84b" stroke="#4a3728" strokeWidth="3.2" strokeLinecap="round" />
          {/* 右翅：遮眼时抬起 */}
          <g className="owl-wing">
            <path
              d="M150 118c10 8 12 22 8 32-12-2-22-10-26-20z"
              fill="#e8b84b"
              stroke="#4a3728"
              strokeWidth="3.2"
              strokeLinejoin="round"
            />
          </g>
        </g>
      </svg>
    </div>
  );
};

export default OwlCharacter;
