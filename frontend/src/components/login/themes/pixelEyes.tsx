import React from 'react';
import { characterClassName, useLoginCharacterEffects } from '../characterEngine';
import type { LoginCharacterProps } from '../types';

/**
 * 主题 `pixel-eyes`：像素方块脸（深绿 / 荧光绿 / 黑）。
 * 眨眼表现是**明暗跳变**（`steps()` 断开渐变，像老显示器）。
 */
const PixelEyesCharacter: React.FC<LoginCharacterProps> = ({
  passwordFocused,
  hasError = false,
  size,
  className,
}) => {
  const { svgRef, pupil, blinking, shaking } = useLoginCharacterEffects(hasError);
  // 像素风对齐到整数格
  const step = 4;
  const px = Math.round((pupil.x * step) / 2) * 2;
  const py = Math.round((pupil.y * step) / 2) * 2;

  return (
    <div className={['login-char', className].filter(Boolean).join(' ')}>
      <svg
        ref={svgRef}
        data-testid="login-char-svg"
        data-theme="pixel-eyes"
        className={characterClassName('theme-pixel', { blinking, shaking, shielding: passwordFocused })}
        viewBox="0 0 200 200"
        style={size ? { maxWidth: size } : undefined}
        aria-hidden="true"
        focusable="false"
        shapeRendering="crispEdges"
      >
        <g className="char-bob">
          {/* 身体（像素块） */}
          <rect x="62" y="126" width="76" height="50" fill="#0f2f1e" stroke="#39ff88" strokeWidth="4" />
          <rect x="74" y="140" width="12" height="12" fill="#39ff88" opacity="0.8" />
          <rect x="114" y="140" width="12" height="12" fill="#39ff88" opacity="0.8" />
          <rect x="84" y="158" width="32" height="8" fill="#39ff88" opacity="0.55" />

          {/* 脸 */}
          <g className="pixel-face">
            <rect x="54" y="44" width="92" height="80" fill="#05150d" stroke="#39ff88" strokeWidth="4" />
            {/* 天线 */}
            <rect x="96" y="24" width="8" height="20" fill="#39ff88" />
            <rect x="92" y="16" width="16" height="10" fill="#39ff88" />

            {/* 眼睛：3x3 像素块 + 瞳孔叠层 */}
            <g className="pixel-eye">
              <rect x="70" y="66" width="24" height="24" fill="#39ff88" />
              <rect x="106" y="66" width="24" height="24" fill="#39ff88" />
            </g>
            <g
              className="char-pupil"
              data-testid="login-char-pupil"
              style={{ transform: `translate(${px}px, ${py}px)` }}
            >
              <rect x="78" y="74" width="8" height="8" fill="#05150d" />
              <rect x="114" y="74" width="8" height="8" fill="#05150d" />
            </g>

            {/* 嘴 */}
            <g className="pixel-mouth">
              <rect x="88" y="104" width="24" height="6" fill="#39ff88" />
            </g>
          </g>
        </g>
      </svg>
    </div>
  );
};

export default PixelEyesCharacter;
