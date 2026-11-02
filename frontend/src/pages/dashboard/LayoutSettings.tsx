import React from 'react';
import { Alert, Button, Checkbox, List, Modal, Select, Space, Tag, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, UndoOutlined } from '@ant-design/icons';
import { useT, type MessageKey } from '@/i18n';
import type { DashboardCardLayout } from '@/types/dashboard';
import { cardTitleKey } from './layoutConfig';

const { Text } = Typography;

const SPAN_OPTIONS = [
  { labelKey: 'dashboard.layout.span24' as MessageKey, value: 24 },
  { labelKey: 'dashboard.layout.span12' as MessageKey, value: 12 },
  { labelKey: 'dashboard.layout.span6' as MessageKey, value: 6 },
];

interface LayoutSettingsProps {
  open: boolean;
  layout: DashboardCardLayout[];
  saving: boolean;
  onCancel: () => void;
  onChange: (layout: DashboardCardLayout[]) => void;
  onSave: () => void;
  onReset: () => void;
}

/**
 * 卡片布局设置：显隐 / 宽度 / 顺序，保存到 POST /dashboard/config。
 */
const LayoutSettings: React.FC<LayoutSettingsProps> = ({ open, layout, saving, onCancel, onChange, onSave, onReset }) => {
  const { t } = useT();
  const move = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= layout.length) return;
    const next = [...layout];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  const toggle = (id: string, visible: boolean) =>
    onChange(layout.map((row) => (row.id === id ? { ...row, visible } : row)));

  const changeSpan = (id: string, span: number) =>
    onChange(layout.map((row) => (row.id === id ? { ...row, span } : row)));

  const visibleCount = layout.filter((row) => row.visible).length;

  return (
    <Modal
      open={open}
      title={t('dashboard.layoutModalTitle')}
      onCancel={onCancel}
      onOk={onSave}
      confirmLoading={saving}
      okText={t('dashboard.layoutSave')}
      width={620}
      footer={[
        <Button key="reset" icon={<UndoOutlined />} onClick={onReset} loading={saving}>
          {t('dashboard.layoutReset')}
        </Button>,
        <Button key="cancel" onClick={onCancel}>
          {t('common.cancel')}
        </Button>,
        <Button key="ok" type="primary" loading={saving} onClick={onSave}>
          {t('dashboard.layoutSave')}
        </Button>,
      ]}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('dashboard.layout.bannerTitle')}
        description={t('dashboard.layout.bannerDesc', { visible: visibleCount, total: layout.length })}
      />
      <List
        size="small"
        dataSource={layout}
        renderItem={(row, index) => (
          <List.Item key={row.id}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
              <Space>
                <Tag color={row.visible ? 'blue' : 'default'}>{index + 1}</Tag>
                <Checkbox checked={row.visible} onChange={(event) => toggle(row.id, event.target.checked)}>
                  <Text delete={!row.visible}>{t(cardTitleKey(row.id))}</Text>
                </Checkbox>
              </Space>
              <Space>
                <Select
                  size="small"
                  style={{ width: 120 }}
                  value={row.span}
                  options={SPAN_OPTIONS.map((item) => ({ label: t(item.labelKey), value: item.value }))}
                  onChange={(value) => changeSpan(row.id, value)}
                />
                <Button
                  size="small"
                  type="text"
                  icon={<ArrowUpOutlined />}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                />
                <Button
                  size="small"
                  type="text"
                  icon={<ArrowDownOutlined />}
                  disabled={index === layout.length - 1}
                  onClick={() => move(index, 1)}
                />
              </Space>
            </Space>
          </List.Item>
        )}
      />
    </Modal>
  );
};

export default LayoutSettings;
