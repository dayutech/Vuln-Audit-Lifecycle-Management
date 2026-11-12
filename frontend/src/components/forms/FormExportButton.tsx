import { useState } from 'react';
import { Button, Dropdown, message } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useT } from '@/i18n';
import { exportFormSubmissions, type FormExportParams } from '@/api/forms';
import { saveBlobAsFile } from '@/utils/download';
import { getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';

/**
 * 表单导出按钮（t183）：「CSV / XLSX」下拉，参数带当前筛选。
 *
 * 错误分级：400（超过 5000 行上限 / 过滤非法）→ 展示后端消息；403 → 无权限；其它 → 通用失败。
 * 文件名优先取 `Content-Disposition` 的 RFC 5987 中文名，缺失时用本地兜底名。
 */
export interface FormExportButtonProps {
  /** 当前筛选（definitionId/status/from/to） */
  params: Omit<FormExportParams, 'format'>;
  /** 兜底文件名（无 Content-Disposition 时用），不含扩展名 */
  fallbackName: string;
  testIdPrefix?: string;
  disabled?: boolean;
}

const FormExportButton: React.FC<FormExportButtonProps> = ({ params, fallbackName, testIdPrefix = 'form-export', disabled }) => {
  const { t } = useT();
  const [exporting, setExporting] = useState<'csv' | 'xlsx' | null>(null);

  const doExport = async (format: 'csv' | 'xlsx') => {
    setExporting(format);
    try {
      const { blob, fileName } = await exportFormSubmissions({ ...params, format });
      const saved = saveBlobAsFile(blob, fileName || `${fallbackName}.${format}`);
      message.success(t('form.export.success', { name: saved }));
    } catch (error) {
      const status = getApiErrorStatus(error);
      if (status === 403) {
        message.error(t('form.export.forbidden'));
      } else if (status === 400) {
        // 400 的 body 在 blob 里，需单独解析后展示后端原因（例如行数上限）
        const backendMessage = await getBlobErrorMessage(error).catch(() => '');
        message.error(backendMessage || t('form.export.tooMany'));
      } else if (status !== 401) {
        message.error(t('form.export.failed'));
      }
    } finally {
      setExporting(null);
    }
  };

  return (
    <Dropdown
      trigger={['click']}
      disabled={disabled}
      menu={{
        items: [
          {
            key: 'csv',
            label: <span data-testid={`${testIdPrefix}-csv`}>{t('form.export.csv')}</span>,
            onClick: () => void doExport('csv'),
          },
          {
            key: 'xlsx',
            label: <span data-testid={`${testIdPrefix}-xlsx`}>{t('form.export.xlsx')}</span>,
            onClick: () => void doExport('xlsx'),
          },
        ],
      }}
    >
      <Button icon={<DownloadOutlined />} loading={Boolean(exporting)} disabled={disabled} data-testid={testIdPrefix}>
        {t('form.export.button')}
      </Button>
    </Dropdown>
  );
};

export default FormExportButton;
