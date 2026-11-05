import React, { useState } from 'react';
import { Alert, Input, Modal, message } from 'antd';
import { getApiErrorMessage, rejectRegistration, type RegistrationRequestVO } from '@/api/registrationReview';
import { getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 拒绝注册申请弹窗（t114）。
 *
 * - 备注**必填**（后端 `RejectRegistrationRequest.note` 是 `@NotBlank`，1–500 字）；
 *   任务口径要求 UI 限制 ≤200 字，这里按更严的 200 卡住并在超限时禁用提交；
 * - 明确告知「拒绝后会释放用户名/手机号，申请人可重新申请」（t111 的语义）；
 * - 失败 403/409/5xx 分级，其它原样透出后端 message。
 */
const NOTE_MAX = 200;

export interface RejectRegistrationModalProps {
  request: RegistrationRequestVO | null;
  onClose: () => void;
  onSucceeded: (payload: { username: string }) => void;
}

const RejectRegistrationModal: React.FC<RejectRegistrationModalProps> = ({ request, onClose, onSucceeded }) => {
  const { t } = useT();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const tooLong = note.length > NOTE_MAX;
  const canSubmit = note.trim().length > 0 && !tooLong && !submitting;

  const submit = async () => {
    if (!request || !canSubmit) {
      if (!note.trim()) setError(t('registrationReview.reject.noteRequired'));
      else if (tooLong) setError(t('registrationReview.reject.noteTooLong', { max: NOTE_MAX }));
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await rejectRegistration(request.id, note);
      message.success(t('registrationReview.reject.success', { username: request.username }));
      onSucceeded({ username: request.username });
    } catch (err) {
      const status = getApiErrorStatus(err);
      const backend = getApiErrorMessage(err);
      if (status === 403) message.error(t('registrationReview.reject.err403'));
      else if (status === 409) message.warning(t('registrationReview.reject.err409'));
      else message.error(t('registrationReview.reject.errGeneric', { message: backend }));
    } finally {
      setSubmitting(false);
    }
  };

  if (!request) return null;

  return (
    <Modal
      open
      title={t('registrationReview.reject.title')}
      onCancel={submitting ? undefined : onClose}
      onOk={submit}
      okText={t('registrationReview.reject.submit')}
      cancelText={t('preferences.close')}
      confirmLoading={submitting}
      okButtonProps={{ disabled: !canSubmit, danger: true, 'data-testid': 'registration-reject-submit' }}
      cancelButtonProps={{ 'data-testid': 'registration-reject-cancel' }}
      destroyOnHidden
    >
      <div data-testid="registration-reject-modal">
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={t('registrationReview.reject.hint')} />
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{t('registrationReview.reject.noteLabel')}</div>
        <Input.TextArea
          value={note}
          onChange={(event) => {
            setNote(event.target.value);
            if (error) setError(null);
          }}
          placeholder={t('registrationReview.reject.notePlaceholder')}
          autoSize={{ minRows: 3, maxRows: 6 }}
          maxLength={NOTE_MAX + 50}
          status={error || tooLong ? 'error' : undefined}
          data-testid="registration-reject-note"
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 12 }}>
          <span style={{ color: '#ef4444' }}>{error ?? (tooLong ? t('registrationReview.reject.noteTooLong', { max: NOTE_MAX }) : '')}</span>
          <span style={{ color: tooLong ? '#ef4444' : '#9ca3af' }} data-testid="registration-reject-note-count">
            {note.length}/{NOTE_MAX}
          </span>
        </div>
      </div>
    </Modal>
  );
};

export default RejectRegistrationModal;
