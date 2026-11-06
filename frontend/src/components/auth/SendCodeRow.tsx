import React from 'react';
import { Button, Input } from 'antd';
import type { InputRef } from 'antd';
import { useT } from '@/i18n';

/**
 * 「接收方 + 获取验证码」同行输入行（t122）。
 *
 * 与 `CodeField`（验证码 + 重发按钮）的区别：这里输入的是**手机号 / 邮箱**，
 * 按钮负责「发码」。注册页把两者拆成两行（手机号+发码同行、验证码单独一行），
 * 页面高度比原来「输入框一行 + 按钮一行」矮一行。
 *
 * 作为 antd `Form.Item` 的受控子组件使用（接收 `value` / `onChange`），
 * 校验错误由 Form.Item 正常渲染在下方；发送逻辑（本地格式校验、429、devCode）由页面负责。
 */
export interface SendCodeRowProps {
  value?: string;
  onChange?: (value: string) => void;
  onSend: () => void;
  /** 剩余倒数秒数（>0 时按钮禁用并显示倒计时） */
  cooldown: number;
  sending?: boolean;
  /** false = 只渲染输入框（例如邮箱未填时不显示发码按钮） */
  showSend?: boolean;
  placeholder?: string;
  prefix?: React.ReactNode;
  autoComplete?: string;
  maxLength?: number;
  inputMode?: 'text' | 'tel' | 'email' | 'numeric';
  disabled?: boolean;
  testId?: string;
  sendTestId?: string;
  onFocus?: () => void;
  onBlur?: () => void;
}

const SendCodeRow = React.forwardRef<InputRef, SendCodeRowProps>(
  (
    {
      value,
      onChange,
      onSend,
      cooldown,
      sending = false,
      showSend = true,
      placeholder,
      prefix,
      autoComplete,
      maxLength,
      inputMode,
      disabled = false,
      testId,
      sendTestId,
      onFocus,
      onBlur,
    },
    ref,
  ) => {
    const { t } = useT();
    const cooling = cooldown > 0;

    return (
      <div className="auth-inline">
        <Input
          ref={ref}
          value={value ?? ''}
          onChange={(event) => onChange?.(event.target.value)}
          placeholder={placeholder}
          prefix={prefix}
          autoComplete={autoComplete}
          maxLength={maxLength}
          inputMode={inputMode}
          disabled={disabled}
          data-testid={testId}
          onFocus={onFocus}
          onBlur={onBlur}
        />
        {showSend ? (
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
        ) : null}
      </div>
    );
  },
);

SendCodeRow.displayName = 'SendCodeRow';

export default SendCodeRow;
