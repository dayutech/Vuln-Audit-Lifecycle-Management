import React from 'react';
import { Button, Input } from 'antd';
import type { InputRef } from 'antd';
import { useT } from '@/i18n';

/**
 * 验证码输入框 + 「获取验证码 / 倒数」按钮（t105）。
 *
 * 作为 antd `Form.Item` 的**受控子组件**（接收 `value` / `onChange`），
 * 这样校验错误由 Form.Item 正常渲染在一行下方，不需要嵌套 Form.Item。
 *
 * 按钮状态（禁用 / 倒数文案）由外层通过 `cooldown`、`sending` 控制，
 * 发送动作本身（含本地格式校验、429、devCode）由页面负责 —— 该组件不碰网络。
 */
export interface CodeFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  onSend: () => void;
  /** 剩余倒数秒数（>0 时按钮禁用并显示倒计时） */
  cooldown: number;
  sending?: boolean;
  placeholder?: string;
  disabled?: boolean;
  /** 无障碍与 E2E 定位用 */
  testId?: string;
  sendTestId?: string;
  maxLength?: number;
  autoComplete?: string;
  onFocus?: () => void;
  onBlur?: () => void;
}

const CodeField = React.forwardRef<InputRef, CodeFieldProps>(
  (
    {
      value,
      onChange,
      onSend,
      cooldown,
      sending = false,
      placeholder,
      disabled = false,
      testId,
      sendTestId,
      maxLength = 6,
      autoComplete = 'one-time-code',
      onFocus,
      onBlur,
    },
    ref,
  ) => {
    const { t } = useT();
    const cooling = cooldown > 0;

    return (
      <div className="auth-code-row">
        <Input
          ref={ref}
          value={value ?? ''}
          onChange={(event) => onChange?.(event.target.value.replace(/\s/g, ''))}
          placeholder={placeholder}
          maxLength={maxLength}
          autoComplete={autoComplete}
          inputMode="numeric"
          disabled={disabled}
          data-testid={testId}
          onFocus={onFocus}
          onBlur={onBlur}
        />
        <Button
          type="default"
          onClick={onSend}
          disabled={disabled || cooling}
          loading={sending}
          data-testid={sendTestId}
          data-cooling={cooling ? 'true' : 'false'}
        >
          {cooling ? t('register.resendIn', { seconds: cooldown }) : t('register.sendCode')}
        </Button>
      </div>
    );
  },
);

CodeField.displayName = 'CodeField';

export default CodeField;
