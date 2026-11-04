import React from 'react';
import { characterClassName, useLoginCharacterEffects } from '../characterEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `pencil`：手绘线条小人（米白 / 墨黑 / 橙）。
 * 焦点进密码框时**抬臂遮眼**并把头微微撇开。
 */
const PencilCharacter: React.FC<LoginCharacterProps> = ({
  passwordFocused,
  hasError = false,
  size,
  className,
}) => {
  const { svgRef, pupil, blinking, shaking } = useLoginCharacterEffects(hasError);
  const limit = 3.2;

  return (
    <div className={['login-char', className].filter(Boolean).join(' ')}>
      <svg
        ref={svgRef}
        data-testid="login-char-svg"
        data-theme="pencil"
        className={characterClassName('theme-pencil', { blinking, shaking, shielding: passwordFocused })}
        viewBox="0 0 200 200"
        style={size ? { maxWidth: size } : undefined}
        aria-hidden="true"
        focusable="false"
      >
        <g className="char-bob">
          {/* 身体 */}
          <path
            d="M78 128c-6 10-9 24-9 34h62c0-10-3-24-9-34"
            fill="#f7f1e6"
            stroke="#1f2328"
            strokeWidth="3.2"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          {/* 左臂（常态垂下） */}
          <path
            d="M76 136c-10 6-16 14-18 24"
            fill="none"
            stroke="#1f2328"
            strokeWidth="3.2"
            strokeLinecap="round"
          />
          {/* 右臂：遮眼时抬起 */}
          <g className="pencil-arm">
            <path
              d="M126 136c12 2 18 12 20 22"
              fill="none"
              stroke="#1f2328"
              strokeWidth="3.2"
              strokeLinecap="round"
            />
            <circle cx="147" cy="160" r="5" fill="#f7f1e6" stroke="#1f2328" strokeWidth="3" />
          </g>

          {/* 头（略微歪一点，手绘感） */}
          <g className="pencil-head">
            <ellipse
              cx="100"
              cy="92"
              rx="37"
              ry="34"
              fill="#f7f1e6"
              stroke="#1f2328"
              strokeWidth="3.2"
              transform="rotate(-2 100 92)"
            />
            {/* 头发两笔 */}
            <path d="M74 70c6-9 16-13 26-13" fill="none" stroke="#1f2328" strokeWidth="3.2" strokeLinecap="round" />
            <path d="M110 60l6-9" fill="none" stroke="#1f2328" strokeWidth="3.2" strokeLinecap="round" />

            {/* 眼睛 */}
            <circle cx="87" cy="91" r="8.5" fill="#fffaf2" stroke="#1f2328" strokeWidth="2.6" />
            <circle cx="113" cy="91" r="8.5" fill="#fffaf2" stroke="#1f2328" strokeWidth="2.6" />
            <g
              className="char-pupil"
              data-testid="login-char-pupil"
              style={{ transform: `translate(${(pupil.x * limit).toFixed(2)}px, ${(pupil.y * limit).toFixed(2)}px)` }}
            >
              <circle cx="87" cy="91" r="3.6" fill="#1f2328" />
              <circle cx="113" cy="91" r="3.6" fill="#1f2328" />
            </g>
            {/* 眼睑（眨眼） */}
            <g className="pencil-lid">
              <ellipse cx="87" cy="91" rx="9" ry="9" fill="#f7f1e6" />
              <ellipse cx="113" cy="91" rx="9" ry="9" fill="#f7f1e6" />
            </g>

            {/* 嘴 + 腮红 */}
            <path
              d="M94 106c3 3.4 9 3.4 12 0"
              fill="none"
              stroke="#1f2328"
              strokeWidth="2.8"
              strokeLinecap="round"
            />
            <circle cx="76" cy="102" r="4" fill="#ff6b35" opacity="0.55" />
            <circle cx="124" cy="102" r="4" fill="#ff6b35" opacity="0.55" />
          </g>
        </g>
      </svg>
    </div>
  );
};

export default PencilCharacter;
