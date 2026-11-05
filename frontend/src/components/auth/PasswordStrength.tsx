import React from 'react';
import { useT } from '@/i18n';
import { evaluatePassword, passwordStrengthLevel } from '@/utils/passwordPolicy';

/**
 * 密码强度提示（t105）—— 与后端 `PasswordPolicy` 同一口径：
 * ≥8 位，且「大写 / 小写 / 数字 / 符号」至少三类。
 *
 * 展示：4 段强度条（弱 / 一般 / 良好 / 很强）+ 规则说明；
 * 是否达标由 `evaluatePassword(...).valid` 决定，页面据此禁用提交按钮。
 */
export interface PasswordStrengthProps {
  value?: string;
}

const PasswordStrength: React.FC<PasswordStrengthProps> = ({ value = '' }) => {
  const { t } = useT();
  const assessment = evaluatePassword(value);
  const level = passwordStrengthLevel(assessment);
  const levelText = t(`passwordStrength.level.${level}`);

  return (
    <div className="pw-strength" data-testid="password-strength" data-level={level} data-valid={assessment.valid ? 'true' : 'false'}>
      <div className="pw-strength__bars" data-testid="password-strength-bars">
        {[1, 2, 3, 4].map((segment) => (
          <span
            key={segment}
            className={['pw-strength__bar', assessment.segments >= segment ? 'is-on' : '', `level-${level}`]
              .filter(Boolean)
              .join(' ')}
          />
        ))}
      </div>
      <div className="pw-strength__meta">
        <span className="pw-strength__rules" data-valid={assessment.valid ? 'true' : 'false'}>
          {assessment.valid ? t('passwordStrength.valid') : t('passwordStrength.rules')}
        </span>
        {value ? (
          <span className="pw-strength__level" data-testid="password-strength-level">
            {t('passwordStrength.label')} · {levelText}
          </span>
        ) : null}
      </div>
    </div>
  );
};

export default PasswordStrength;
