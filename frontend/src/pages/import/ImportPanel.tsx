import React, { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  InboxOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import {
  IMPORT_MAX_FILE_SIZE,
  IMPORT_TYPE_META,
  TEAM_SCOPE_NOTICE,
  downloadImportTemplate,
  extractImportResult,
  importCsv,
  isPermissionDeniedFailure,
  splitImportFailures,
  validateCsvFile,
} from '@/api/import';
import type { ImportFailedRow, ImportResult, ImportType, ImportedUser } from '@/api/import';
import { saveBlobAsFile } from '@/utils/download';
import { getApiErrorMessage, getApiErrorStatus, getBlobErrorMessage } from '@/utils/errorMessage';
import { useT, type MessageKey } from '@/i18n';

const { Text, Paragraph } = Typography;

interface ImportErrorState {
  status?: number;
  message: string;
}

interface ImportPanelProps {
  type: ImportType;
  /** 团队长视角：资产导入 Tab 顶部提示团队范围限制 */
  teamScopeHint?: boolean;
}

/**
 * 单个导入面板（资产 / 漏洞 / 用户各一个 Tab，共用同一套结果组件）。
 *
 * 语义区分：
 * - HTTP 400：文件级失败（空文件 / 表头缺列 / 超 20MB），整单不入库 → 红色 Alert；
 * - HTTP 200 且 failedRows 非空：部分成功，成功行已入库 → 黄色 Alert + 失败行表格；
 * - failedRows 内再按「权限拒绝：」前缀拆分权限类失败与数据校验失败（t33 常量），
 *   权限类失败**不会写入库**，需与数据错误分开统计与展示。
 *
 * 用户导入额外返回 createdUsers（含一次性初始密码）：只存在于组件内存，
 * 不做 localStorage/接口持久化，刷新或离开页面即丢失。
 */
const ImportPanel: React.FC<ImportPanelProps> = ({ type, teamScopeHint = false }) => {
  const { t } = useT();
  const meta = IMPORT_TYPE_META[type];
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [backendMessage, setBackendMessage] = useState<string | undefined>(undefined);
  const [importError, setImportError] = useState<ImportErrorState | null>(null);
  const [importing, setImporting] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const createdUsers = result?.createdUsers ?? [];
  const failedRows = result?.failedRows ?? [];
  /** t78：成功行提示（旧后端无 notes 字段 → 空数组，不渲染区块） */
  const notes = result?.notes ?? [];
  const { permissionDenied, dataErrors } = useMemo(
    () => splitImportFailures(failedRows),
    [failedRows],
  );

  const successRatio = useMemo(() => {
    if (!result || result.totalRows <= 0) return 0;
    return Math.round((result.successRows / result.totalRows) * 100);
  }, [result]);

  const handleSelect = (file: File) => {
    const invalid = validateCsvFile(file);
    if (invalid) {
      setSelectedFile(null);
      setFileError(invalid);
      message.error(invalid);
      return;
    }
    setFileError(null);
    setImportError(null);
    setResult(null);
    setBackendMessage(undefined);
    setSelectedFile(file);
  };

  const handleDownloadTemplate = async () => {
    setDownloading(true);
    try {
      const blob = await downloadImportTemplate(type);
      const savedName = saveBlobAsFile(blob as Blob, meta.templateFile);
      message.success(t('feedback.import.templateDownloaded', { name: savedName }));
    } catch (error) {
      const text = await getBlobErrorMessage(error, t('feedback.import.templateDownloadFailed'));
      message.error(text);
    } finally {
      setDownloading(false);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) {
      message.warning(t('feedback.import.noFile'));
      return;
    }
    const invalid = validateCsvFile(selectedFile);
    if (invalid) {
      setFileError(invalid);
      message.error(invalid);
      return;
    }

    setImporting(true);
    setImportError(null);
    setResult(null);
    setBackendMessage(undefined);
    try {
      const res = await importCsv(type, selectedFile);
      const { result: parsed, message: msg } = extractImportResult(res);
      setResult(parsed);
      setBackendMessage(msg);
      if (parsed.failedRows.length === 0) {
        message.success(t('feedback.import.success', { count: parsed.successRows }));
      } else {
        message.warning(t('feedback.import.partialSuccess', { success: parsed.successRows, failed: parsed.failedRows.length }));
      }
    } catch (error) {
      const status = getApiErrorStatus(error);
      setImportError({ status, message: getApiErrorMessage(error, t('feedback.import.failed')) });
    } finally {
      setImporting(false);
    }
  };

  const handleReset = () => {
    setSelectedFile(null);
    setFileError(null);
    setResult(null);
    setBackendMessage(undefined);
    setImportError(null);
  };

  const copyCreatedAccounts = async () => {
    if (createdUsers.length === 0) return;
    const text = createdUsers
      .map((user) => `${user.username}\t${user.initialPassword}\t${user.roleCode}`)
      .join('\n');
    try {
      await navigator.clipboard.writeText(`${t('import.page.clipboardHeader')}\n${text}`);
      message.success(t('feedback.import.credentialsCopied', { count: createdUsers.length }));
    } catch {
      message.warning(t('feedback.import.clipboardDenied'));
    }
  };

  const failedColumns = [
    {
      title: t('import.colType'),
      key: 'kind',
      width: 84,
      render: (_: unknown, row: ImportFailedRow) =>
        isPermissionDeniedFailure(row) ? <Tag color="red">{t('import.page.permTagShort')}</Tag> : <Tag>{t('import.page.dataTagShort')}</Tag>,
    },
    {
      title: t('import.colRowNumber'),
      dataIndex: 'line',
      key: 'line',
      width: 100,
      render: (line: number) => <Tag color="red">{t('import.page.rowLine', { line })}</Tag>,
    },
    {
      title: t('import.colFailure'),
      dataIndex: 'reason',
      key: 'reason',
      render: (reason: string) => <Text type="danger">{reason || t('import.page.reasonMissing')}</Text>,
    },
  ];

  const createdColumns = [
    {
      title: t('import.colRowNumber'),
      dataIndex: 'line',
      key: 'line',
      width: 90,
      render: (line: number) => <Tag>{t('import.page.rowLine', { line })}</Tag>,
    },
    {
      title: t('import.colUsername'),
      dataIndex: 'username',
      key: 'username',
      render: (username: string) => <Text copyable={{ text: username }}>{username}</Text>,
    },
    {
      title: t('import.colInitialPassword'),
      dataIndex: 'initialPassword',
      key: 'initialPassword',
      render: (password: string) => (
        <Text code copyable={{ text: password }}>
          {password}
        </Text>
      ),
    },
    {
      title: t('import.colRoles'),
      dataIndex: 'roleCode',
      key: 'roleCode',
      render: (roleCode: string) => <Tag color="blue">{roleCode}</Tag>,
    },
    {
      title: t('import.colMustChangePassword'),
      dataIndex: 'mustChangePassword',
      key: 'mustChangePassword',
      width: 90,
      render: (mustChange: boolean) =>
        mustChange ? <Tag color="orange">{t('import.page.yes')}</Tag> : <Tag color="default">{t('import.page.no')}</Tag>,
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <style>{`.import-failed-row > td { background: #fff1f0 !important; }`}</style>
      <style>{`.import-permission-row > td { background: #fff7e6 !important; }`}</style>

      {teamScopeHint && type === 'assets' && (
        <Alert
          type="warning"
          showIcon
          message={t('import.page.teamScopeTitle')}
          description={TEAM_SCOPE_NOTICE}
        />
      )}

      <Descriptions
        column={1}
        size="small"
        bordered
        items={[
          { key: 'columns', label: t('import.page.fieldColumns'), children: <Text code>{t(meta.columnsKey)}</Text> },
          { key: 'tips', label: t('import.page.fieldTips'), children: t(meta.tipsKey) },
          {
            key: 'limit',
            label: t('import.page.fieldLimit'),
            children: t('import.page.fileLimitText', { size: IMPORT_MAX_FILE_SIZE / 1024 / 1024 }),
          },
        ]}
      />

      <Space wrap>
        <Button icon={<CloudDownloadOutlined />} loading={downloading} onClick={handleDownloadTemplate}>
          {t('import.page.downloadTemplate')}
        </Button>
        <Text type="secondary">{t('import.page.bomHint')}</Text>
      </Space>

      <Upload.Dragger
        accept=".csv,text/csv"
        maxCount={1}
        multiple={false}
        beforeUpload={(file) => {
          handleSelect(file as unknown as File);
          return false;
        }}
        onRemove={() => {
          setSelectedFile(null);
          setResult(null);
          setImportError(null);
        }}
        fileList={
          selectedFile
            ? [
                {
                  uid: `${type}-selected`,
                  name: selectedFile.name,
                  size: selectedFile.size,
                  status: 'done',
                } as any,
              ]
            : []
        }
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{t('import.page.dropText')}</p>
        <p className="ant-upload-hint">{t('import.page.dropHint', { size: 20 })}</p>
      </Upload.Dragger>

      {fileError && <Alert type="error" showIcon message={t('import.page.fileInvalidTitle')} description={fileError} />}

      <Space>
        <Button
          type="primary"
          icon={<CloudUploadOutlined />}
          loading={importing}
          disabled={!selectedFile}
          onClick={handleImport}
        >
          {t('import.page.startImport')}
        </Button>
        <Button icon={<ReloadOutlined />} onClick={handleReset} disabled={importing}>
          {t('import.page.reset')}
        </Button>
        <Link to={meta.listPath}>
          <Button type="link">{t('import.goToList', { label: t(meta.listLabelKey) })}</Button>
        </Link>
      </Space>

      {importError && (
        <Alert
          type="error"
          showIcon
          message={describeErrorTitle(importError.status, t)}
          description={
            <div>
              <p style={{ marginBottom: 8 }}>{importError.message}</p>
              {importError.status === 400 && (
                <p style={{ margin: 0 }}>
                  {t('import.page.err400', { size: 20 })}<Text code>{t(meta.columnsKey)}</Text>
                </p>
              )}
              {importError.status === 413 && (
                <p style={{ margin: 0 }}>{t('import.page.err413', { size: 20 })}</p>
              )}
              {importError.status === 403 && meta.roleGate !== 'public' && (
                <p style={{ margin: 0 }}>{t('import.page.err403')}</p>
              )}
            </div>
          }
        />
      )}

      {result && (
        <Card
          size="small"
          title={t('import.resultTitle')}
          extra={
            result.successRows > 0 ? (
              <Link to={meta.listPath}>
                <Button type="link" size="small">{t('import.goToList', { label: t(meta.listLabelKey) })}</Button>
              </Link>
            ) : null
          }
        >
          <Alert
            type={failedRows.length > 0 ? 'warning' : 'success'}
            showIcon
            style={{ marginBottom: 16 }}
            message={
              backendMessage
                ?? (failedRows.length > 0
                  ? t('import.page.partialTitle', { success: result.successRows, failed: failedRows.length })
                  : t('import.page.successTitle', { success: result.successRows }))
            }
            description={
              failedRows.length > 0
                ? (
                  <div>
                    <p style={{ margin: '0 0 4px' }}>
                      {t('import.page.partialDesc')}
                    </p>
                    {permissionDenied.length > 0 && (
                      <p style={{ margin: '0 0 4px' }}>
                        <Tag color="red">{t('import.page.permissionTag')}</Tag>
                        {t('import.page.permissionDesc', { count: permissionDenied.length })}
                      </p>
                    )}
                    <p style={{ margin: 0 }}>
                      {t('import.page.retryHint')}
                    </p>
                  </div>
                )
                : t('import.page.allImported')
            }
          />

          {notes.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16, borderColor: '#ffe58f', background: '#fffbe6' }}
              message={t('import.notes.title')}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {notes.map((note) => (
                    <li key={`${note.line}-${note.message}`}>
                      {t('import.notes.line', { line: note.line, message: note.message })}
                    </li>
                  ))}
                </ul>
              }
            />
          )}

          <Space size={48} wrap style={{ marginBottom: failedRows.length > 0 || createdUsers.length > 0 || notes.length > 0 ? 16 : 0 }}>
            <Statistic title={t('import.statTotalRows')} value={result.totalRows} />
            <Statistic
              title={t('import.statSuccessRows')}
              value={result.successRows}
              valueStyle={{ color: '#389e0d' }}
              suffix={notes.length > 0 ? t('import.notes.successWithNotes', { count: notes.length }) : undefined}
            />
            {notes.length > 0 && (
              <Statistic
                title={t('import.notes.stat')}
                value={notes.length}
                valueStyle={{ color: '#d46b08' }}
              />
            )}
            <Statistic
              title={t('import.page.statDataErrors')}
              value={dataErrors.length}
              valueStyle={{ color: dataErrors.length > 0 ? '#cf1322' : undefined }}
            />
            <Statistic
              title={t('import.page.statPermissionDenied')}
              value={permissionDenied.length}
              valueStyle={{ color: permissionDenied.length > 0 ? '#d46b08' : undefined }}
            />
            <Statistic title={t('import.page.statSuccessRate')} value={successRatio} suffix="%" />
          </Space>

          {failedRows.length > 0 && (
            <>
              <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                {t('import.page.failedRowsHint')}
                {permissionDenied.length > 0 && (
                  <Text type="warning">{t('import.page.failedRowsPermissionHint')}</Text>
                )}
              </Paragraph>
              <Table<ImportFailedRow>
                size="small"
                rowKey={(row) => `${row.line}-${row.reason}`}
                dataSource={failedRows}
                columns={failedColumns}
                pagination={false}
                rowClassName={(row) =>
                  isPermissionDeniedFailure(row) ? 'import-permission-row' : 'import-failed-row'
                }
              />
            </>
          )}

          {createdUsers.length > 0 && (
            <div style={{ marginTop: failedRows.length > 0 ? 20 : 0 }}>
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message={t('import.page.passwordTitle')}
                description={
                  t('import.page.passwordDesc')
                }
                action={
                  <Button size="small" icon={<CopyOutlined />} onClick={copyCreatedAccounts}>
                    {t('import.page.copyAll')}
                  </Button>
                }
              />
              <Table<ImportedUser>
                size="small"
                rowKey={(row) => row.userId || `${row.line}-${row.username}`}
                dataSource={createdUsers}
                columns={createdColumns}
                pagination={false}
                scroll={{ x: 720 }}
              />
            </div>
          )}
        </Card>
      )}
    </Space>
  );
};

/** 按状态码给出不同的失败标题（区分 400 / 413 / 403 / 其它） */
const describeErrorTitle = (
  status: number | undefined,
  t: (key: MessageKey, vars?: Record<string, string | number>) => string
): string => {
  switch (status) {
    case 400:
      return t('import.page.errTitle400');
    case 413:
      return t('import.page.errTitle413');
    case 401:
      return t('import.page.errTitle401');
    case 403:
      return t('import.page.errTitle403');
    default:
      return status ? t('import.page.errTitleGeneric', { status }) : t('import.page.errTitleFallback');
  }
};

export default ImportPanel;
