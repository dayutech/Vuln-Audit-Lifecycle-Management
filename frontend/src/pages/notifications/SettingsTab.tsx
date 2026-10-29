import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Empty, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getNotificationSettings, updateNotificationSetting } from '@/api/notification';
import type { NotificationSettingUpdateRequest, NotificationSettingVO } from '@/types/notification';
import { NOTIFICATION_TYPE_OPTIONS } from '@/types/notification';

const { Text } = Typography;

type ChannelKey = 'enabled' | 'notifyEmail' | 'notifySms' | 'notifyWebhook';

interface SettingRow extends NotificationSettingVO {
  /** 后端是否已有该类型的设置记录，用于展示“默认值” */
  persisted: boolean;
  saving: boolean;
}

/** 后端 NotificationSetting 的默认值（实体 @PrePersist） */
const DEFAULT_SETTING: Omit<NotificationSettingVO, 'type'> = {
  enabled: true,
  notifyEmail: false,
  notifySms: false,
  notifyWebhook: false,
};

const buildRows = (settings: NotificationSettingVO[]): SettingRow[] => {
  const byType = new Map(settings.map((item) => [item.type, item]));
  const known = NOTIFICATION_TYPE_OPTIONS.map((option) => {
    const found = byType.get(option.value);
    if (found) {
      return {
        ...found,
        type: option.value,
        enabled: found.enabled ?? DEFAULT_SETTING.enabled,
        notifyEmail: found.notifyEmail ?? DEFAULT_SETTING.notifyEmail,
        notifySms: found.notifySms ?? DEFAULT_SETTING.notifySms,
        notifyWebhook: found.notifyWebhook ?? DEFAULT_SETTING.notifyWebhook,
        persisted: true,
        saving: false,
      };
    }
    return {
      type: option.value,
      ...DEFAULT_SETTING,
      persisted: false,
      saving: false,
    };
  });
  // 后端可能存在不在前端类型目录里的历史设置行（如早期按渠道名写入的 type），
  // 一并展示，避免用户看不到已保存的配置。
  const extra = settings
    .filter((item) => !NOTIFICATION_TYPE_OPTIONS.some((option) => option.value === item.type))
    .map<SettingRow>((item) => ({
      ...item,
      enabled: item.enabled ?? DEFAULT_SETTING.enabled,
      notifyEmail: item.notifyEmail ?? DEFAULT_SETTING.notifyEmail,
      notifySms: item.notifySms ?? DEFAULT_SETTING.notifySms,
      notifyWebhook: item.notifyWebhook ?? DEFAULT_SETTING.notifyWebhook,
      persisted: true,
      saving: false,
    }));
  return [...known, ...extra];
};

/** 表单项 → 后端 PUT 请求体（后端按 snake_case 取键） */
const toPayload = (row: SettingRow): NotificationSettingUpdateRequest => ({
  enabled: row.enabled,
  notify_email: row.notifyEmail,
  notify_sms: row.notifySms,
  notify_webhook: row.notifyWebhook,
});

const SettingsTab: React.FC = () => {
  const [rows, setRows] = useState<SettingRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const settings = await getNotificationSettings();
      setRows(buildRows(settings));
    } catch (error: any) {
      setRows(buildRows([]));
      if (!error?.response) setLoadError('通知设置加载失败，可稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleToggle = async (type: string, key: ChannelKey, checked: boolean) => {
    const snapshot = rows;
    const nextRows = rows.map((row) =>
      row.type === type ? { ...row, [key]: checked, saving: true } : row
    );
    setRows(nextRows);
    const target = nextRows.find((row) => row.type === type);
    if (!target) return;
    try {
      await updateNotificationSetting(type, toPayload(target));
      setRows((current) =>
        current.map((row) => (row.type === type ? { ...row, persisted: true, saving: false } : row))
      );
      message.success('通知设置已保存');
    } catch {
      setRows(snapshot);
      message.error('保存失败，已回滚到上一次设置');
    }
  };

  const tooltipFor = (key: ChannelKey): string => {
    switch (key) {
      case 'notifyEmail':
        return '邮件通道需服务端开启 app.notify.mail.enabled 并配置 SMTP；未开启时仅记录偏好，仍以站内信送达';
      case 'notifyWebhook':
        return 'Webhook 通道需服务端开启 app.notify.webhook.enabled 并配置推送地址；未开启时仅记录偏好';
      case 'notifySms':
        return '短信网关尚未接入，当前仅保存开关位，不会真实下发';
      default:
        return '关闭后该类型不再走邮件/Webhook 等外部通道；站内信仍会写入（后端仅在分发阶段按设置过滤外部通道）';
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '通知类型',
        dataIndex: 'type',
        key: 'type',
        render: (type: string, record: SettingRow) => {
          const option = NOTIFICATION_TYPE_OPTIONS.find((item) => item.value === type);
          return (
            <Space direction="vertical" size={0}>
              <Space size={6}>
                <Text strong>{option?.label ?? type}</Text>
                <Tag>{type}</Tag>
                {!record.persisted ? <Tag color="default">默认值</Tag> : null}
              </Space>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {option?.description ?? '自定义通知类型'}
              </Text>
            </Space>
          );
        },
      },
      {
        title: (
          <Tooltip title={tooltipFor('enabled')}>
            <span>启用通知</span>
          </Tooltip>
        ),
        dataIndex: 'enabled',
        key: 'enabled',
        width: 110,
        render: (value: boolean, record: SettingRow) => (
          <Switch
            checked={value}
            loading={record.saving}
            onChange={(checked) => handleToggle(record.type, 'enabled', checked)}
          />
        ),
      },
      {
        title: (
          <Tooltip title={tooltipFor('notifyEmail')}>
            <span>邮件</span>
          </Tooltip>
        ),
        dataIndex: 'notifyEmail',
        key: 'notifyEmail',
        width: 110,
        render: (value: boolean, record: SettingRow) => (
          <Switch
            checked={value}
            loading={record.saving}
            disabled={!record.enabled}
            onChange={(checked) => handleToggle(record.type, 'notifyEmail', checked)}
          />
        ),
      },
      {
        title: (
          <Tooltip title={tooltipFor('notifySms')}>
            <span>短信</span>
          </Tooltip>
        ),
        dataIndex: 'notifySms',
        key: 'notifySms',
        width: 110,
        render: (value: boolean, record: SettingRow) => (
          <Switch
            checked={value}
            loading={record.saving}
            disabled={!record.enabled}
            onChange={(checked) => handleToggle(record.type, 'notifySms', checked)}
          />
        ),
      },
      {
        title: (
          <Tooltip title={tooltipFor('notifyWebhook')}>
            <span>Webhook</span>
          </Tooltip>
        ),
        dataIndex: 'notifyWebhook',
        key: 'notifyWebhook',
        width: 110,
        render: (value: boolean, record: SettingRow) => (
          <Switch
            checked={value}
            loading={record.saving}
            disabled={!record.enabled}
            onChange={(checked) => handleToggle(record.type, 'notifyWebhook', checked)}
          />
        ),
      },
    ],
    // handleToggle 通过 rows 闭包读取最新值，依赖 rows 即可
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows]
  );

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="通道可用性由服务端决定"
        description="站内信始终送达。邮件与 Webhook 默认关闭（app.notify.mail.enabled / app.notify.webhook.enabled 均为 false），未启用时消息只落站内信，投递状态会记录为“仅站内”并在详情里展示；短信仅保留开关位。"
      />
      {loadError ? <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={loadError} /> : null}
      <Card
        size="small"
        title="按通知类型配置渠道"
        extra={
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}>
            重新加载
          </Button>
        }
      >
        <Table
          rowKey="type"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="没有可配置的通知类型"
              />
            ),
          }}
        />
        <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
          设置按 (用户, 通知类型) 逐条保存，切换开关即触发 PUT /notifications/settings/&lt;type&gt;。
        </Text>
      </Card>
    </div>
  );
};

export default SettingsTab;
