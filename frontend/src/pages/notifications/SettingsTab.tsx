// TODO(i18n): 本页文案仍为中文硬编码，后续按域迁移到 src/i18n/messages/*（覆盖清单见 t53 报告）
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Empty, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getNotificationSettings, updateNotificationSetting } from '@/api/notification';
import type { NotificationSettingUpdateRequest, NotificationSettingVO } from '@/types/notification';
import { NOTIFICATION_TYPE_LABEL_KEYS, NOTIFICATION_TYPE_OPTIONS } from '@/types/notification';
import { useT } from '@/i18n';

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
  const { t } = useT();
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
      if (!error?.response) setLoadError(t('notifications.settings.loadFailed'));
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
      message.success(t('notifications.settings.saved'));
    } catch {
      setRows(snapshot);
      message.error(t('notifications.settings.saveFailed'));
    }
  };

  const tooltipFor = (key: ChannelKey): string => {
    switch (key) {
      case 'notifyEmail':
        return t('notifications.settings.tipEmail');
      case 'notifyWebhook':
        return t('notifications.settings.tipWebhook');
      case 'notifySms':
        return t('notifications.settings.tipSms');
      default:
        return t('notifications.settings.tipEnabled');
    }
  };

  const columns = useMemo(
    () => [
      {
        title: t('notifications.settings.colType'),
        dataIndex: 'type',
        key: 'type',
        render: (type: string, record: SettingRow) => {
          const option = NOTIFICATION_TYPE_OPTIONS.find((item) => item.value === type);
          return (
            <Space direction="vertical" size={0}>
              <Space size={6}>
                <Text strong>{option ? t(NOTIFICATION_TYPE_LABEL_KEYS[option.value] ?? 'notifications.type.unknown') : type}</Text>
                <Tag>{type}</Tag>
                {!record.persisted ? <Tag color="default">{t('notifications.settings.defaultValue')}</Tag> : null}
              </Space>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {option?.description ?? t('notifications.settings.customType')}
              </Text>
            </Space>
          );
        },
      },
      {
        title: (
          <Tooltip title={tooltipFor('enabled')}>
            <span>{t('notifications.settings.enabled')}</span>
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
            <span>{t('notifications.settings.email')}</span>
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
            <span>{t('notifications.settings.sms')}</span>
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
            <span>{t('notifications.settings.webhook')}</span>
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
    // handleToggle 通过 rows 闭包读取最新值，依赖 rows/t 即可
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows, t]
  );

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={t('notifications.settings.bannerTitle')}
        description={t('notifications.settings.bannerDesc')}
      />
      {loadError ? <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={loadError} /> : null}
      <Card
        size="small"
        title={t('notifications.settings.title')}
        extra={
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}>
            {t('notifications.settings.reload')}
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
                description={t('notifications.settings.empty')}
              />
            ),
          }}
        />
        <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
          {t('notifications.settings.footer')}
        </Text>
      </Card>
    </div>
  );
};

export default SettingsTab;
