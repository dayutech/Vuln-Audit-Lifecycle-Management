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
      message.success(`模板已下载：${savedName}`);
    } catch (error) {
      const text = await getBlobErrorMessage(error, '模板下载失败');
      message.error(text);
    } finally {
      setDownloading(false);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) {
      message.warning('请先选择要导入的 CSV 文件');
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
        message.success(`导入成功 ${parsed.successRows} 行`);
      } else {
        message.warning(`导入完成：成功 ${parsed.successRows} 行，失败 ${parsed.failedRows.length} 行`);
      }
    } catch (error) {
      const status = getApiErrorStatus(error);
      setImportError({ status, message: getApiErrorMessage(error, '导入失败') });
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
      await navigator.clipboard.writeText(`用户名\t初始密码\t角色\n${text}`);
      message.success(`已复制 ${createdUsers.length} 个账号的用户名与初始密码`);
    } catch {
      message.warning('浏览器不允许自动复制，请用表格里的复制图标逐个复制');
    }
  };

  const failedColumns = [
    {
      title: '类型',
      key: 'kind',
      width: 84,
      render: (_: unknown, row: ImportFailedRow) =>
        isPermissionDeniedFailure(row) ? <Tag color="red">权限</Tag> : <Tag>数据</Tag>,
    },
    {
      title: '行号',
      dataIndex: 'line',
      key: 'line',
      width: 100,
      render: (line: number) => <Tag color="red">第 {line} 行</Tag>,
    },
    {
      title: '失败原因',
      dataIndex: 'reason',
      key: 'reason',
      render: (reason: string) => <Text type="danger">{reason || '（后端未返回原因）'}</Text>,
    },
  ];

  const createdColumns = [
    {
      title: '行号',
      dataIndex: 'line',
      key: 'line',
      width: 90,
      render: (line: number) => <Tag>第 {line} 行</Tag>,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (username: string) => <Text copyable={{ text: username }}>{username}</Text>,
    },
    {
      title: '初始密码（仅本次显示）',
      dataIndex: 'initialPassword',
      key: 'initialPassword',
      render: (password: string) => (
        <Text code copyable={{ text: password }}>
          {password}
        </Text>
      ),
    },
    {
      title: '角色',
      dataIndex: 'roleCode',
      key: 'roleCode',
      render: (roleCode: string) => <Tag color="blue">{roleCode}</Tag>,
    },
    {
      title: '需改密',
      dataIndex: 'mustChangePassword',
      key: 'mustChangePassword',
      width: 90,
      render: (mustChange: boolean) =>
        mustChange ? <Tag color="orange">是</Tag> : <Tag color="default">否</Tag>,
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
          message="团队长导入范围提示"
          description={TEAM_SCOPE_NOTICE}
        />
      )}

      <Descriptions
        column={1}
        size="small"
        bordered
        items={[
          { key: 'columns', label: '模板列', children: <Text code>{meta.columns}</Text> },
          { key: 'tips', label: '填写须知', children: meta.tips },
          {
            key: 'limit',
            label: '文件要求',
            children: `UTF-8 编码 CSV，首行为表头，单文件 ≤ ${IMPORT_MAX_FILE_SIZE / 1024 / 1024}MB`,
          },
        ]}
      />

      <Space wrap>
        <Button icon={<CloudDownloadOutlined />} loading={downloading} onClick={handleDownloadTemplate}>
          下载模板
        </Button>
        <Text type="secondary">模板自带 UTF-8 BOM，用 Excel 打开中文不乱码</Text>
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
        <p className="ant-upload-text">点击或拖拽 CSV 文件到此处</p>
        <p className="ant-upload-hint">仅 .csv，单文件 ≤ 20MB；前端会先校验扩展名与体积</p>
      </Upload.Dragger>

      {fileError && <Alert type="error" showIcon message="文件校验未通过" description={fileError} />}

      <Space>
        <Button
          type="primary"
          icon={<CloudUploadOutlined />}
          loading={importing}
          disabled={!selectedFile}
          onClick={handleImport}
        >
          开始导入
        </Button>
        <Button icon={<ReloadOutlined />} onClick={handleReset} disabled={importing}>
          清空
        </Button>
        <Link to={meta.listPath}>
          <Button type="link">前往{meta.listLabel}（进入即重新拉取数据）</Button>
        </Link>
      </Space>

      {importError && (
        <Alert
          type="error"
          showIcon
          message={describeErrorTitle(importError.status)}
          description={
            <div>
              <p style={{ marginBottom: 8 }}>{importError.message}</p>
              {importError.status === 400 && (
                <p style={{ margin: 0 }}>
                  400 表示文件级校验未通过（空文件 / 表头缺列 / 超过 20MB），本次没有任何数据入库。
                  请核对表头是否包含：<Text code>{meta.columns}</Text>
                </p>
              )}
              {importError.status === 413 && (
                <p style={{ margin: 0 }}>网关判定请求体过大，请确认文件 ≤ 20MB 后重试。</p>
              )}
              {importError.status === 403 && meta.adminOnly && (
                <p style={{ margin: 0 }}>用户导入仅管理员可用，请使用管理员账号操作。</p>
              )}
            </div>
          }
        />
      )}

      {result && (
        <Card
          size="small"
          title="导入结果"
          extra={
            result.successRows > 0 ? (
              <Link to={meta.listPath}>
                <Button type="link" size="small">查看{meta.listLabel}</Button>
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
                  ? `导入完成：成功 ${result.successRows} 行，失败 ${failedRows.length} 行`
                  : `导入成功 ${result.successRows} 行`)
            }
            description={
              failedRows.length > 0
                ? (
                  <div>
                    <p style={{ margin: '0 0 4px' }}>
                      接口返回 200 只代表请求被处理：其余行已经成功入库，失败行请按 failedRows 逐行核对。
                    </p>
                    {permissionDenied.length > 0 && (
                      <p style={{ margin: '0 0 4px' }}>
                        <Tag color="red">权限</Tag>
                        其中 {permissionDenied.length} 行是<strong>写权限被拒</strong>（不会写入库，不是数据格式问题），
                        需换用有权限的团队/资产或让管理员调整授权后单独重传这些行。
                      </p>
                    )}
                    <p style={{ margin: 0 }}>
                      请勿整份重传：已成功的行会再次插入，建议只保留需要修正的行重新导入。
                    </p>
                  </div>
                )
                : '全部数据行均已入库。'
            }
          />

          <Space size={48} wrap style={{ marginBottom: failedRows.length > 0 || createdUsers.length > 0 ? 16 : 0 }}>
            <Statistic title="数据行总数" value={result.totalRows} />
            <Statistic title="成功入库" value={result.successRows} valueStyle={{ color: '#389e0d' }} />
            <Statistic
              title="数据错误"
              value={dataErrors.length}
              valueStyle={{ color: dataErrors.length > 0 ? '#cf1322' : undefined }}
            />
            <Statistic
              title="权限拒绝"
              value={permissionDenied.length}
              valueStyle={{ color: permissionDenied.length > 0 ? '#d46b08' : undefined }}
            />
            <Statistic title="成功率" value={successRatio} suffix="%" />
          </Space>

          {failedRows.length > 0 && (
            <>
              <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                失败行明细（line 为 CSV 原始行号，表头算第 1 行）：
                {permissionDenied.length > 0 && (
                  <Text type="warning"> 带「权限」标签的行不会写入库。</Text>
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
                message="初始密码仅显示一次，请立即保存并线下分发"
                description={
                  '密码由后端为每行随机生成，本次响应之后无法再次查看；刷新或离开本页即丢失。'
                  + '请通过安全渠道分发，并提醒用户首次登录后立即修改密码。'
                }
                action={
                  <Button size="small" icon={<CopyOutlined />} onClick={copyCreatedAccounts}>
                    复制全部
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
const describeErrorTitle = (status?: number): string => {
  switch (status) {
    case 400:
      return '文件校验未通过（HTTP 400）：整单未入库';
    case 413:
      return '文件超过网关上限（HTTP 413）';
    case 401:
      return '登录已失效（HTTP 401），请重新登录后重试';
    case 403:
      return '没有导入权限（HTTP 403）';
    default:
      return status ? `导入失败（HTTP ${status}）` : '导入失败';
  }
};

export default ImportPanel;
