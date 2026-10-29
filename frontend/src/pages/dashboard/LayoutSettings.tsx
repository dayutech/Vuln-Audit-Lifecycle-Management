import React from 'react';
import { Alert, Button, Checkbox, List, Modal, Select, Space, Tag, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, UndoOutlined } from '@ant-design/icons';
import type { DashboardCardLayout } from '@/types/dashboard';
import { cardTitle } from './layoutConfig';

const { Text } = Typography;

const SPAN_OPTIONS = [
  { label: '整行 24', value: 24 },
  { label: '半行 12', value: 12 },
  { label: '1/4 行 6', value: 6 },
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
      title="自定义大盘布局"
      onCancel={onCancel}
      onOk={onSave}
      confirmLoading={saving}
      okText="保存布局"
      width={620}
      footer={[
        <Button key="reset" icon={<UndoOutlined />} onClick={onReset} loading={saving}>
          恢复默认
        </Button>,
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="ok" type="primary" loading={saving} onClick={onSave}>
          保存布局
        </Button>,
      ]}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="布局按账号保存在 /dashboard/config；未保存过配置时使用默认布局兜底"
        description={`当前启用 ${visibleCount} / ${layout.length} 个卡片，关闭的卡片不会发起对应请求。`}
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
                  <Text delete={!row.visible}>{cardTitle(row.id)}</Text>
                </Checkbox>
              </Space>
              <Space>
                <Select
                  size="small"
                  style={{ width: 120 }}
                  value={row.span}
                  options={SPAN_OPTIONS}
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
