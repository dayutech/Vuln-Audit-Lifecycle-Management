import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AutoComplete,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  addAssetToGroup,
  createAssetGroup,
  deleteAssetGroup,
  getAssetGroupAssets,
  getAssetGroupList,
  getAssetList,
  removeAssetFromGroup,
  updateAssetGroup,
} from '@/api/asset';
import type { AssetGroupVO, AssetVO } from '@/types/asset';

const { TextArea } = Input;

const GROUP_TYPE_OPTIONS = ['客户', '项目', '自建'];

const GROUP_TYPE_COLORS: Record<string, string> = {
  客户: 'blue',
  项目: 'purple',
  自建: 'default',
};

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

const AssetGroups: React.FC = () => {
  const [groups, setGroups] = useState<AssetGroupVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [groupAssetIds, setGroupAssetIds] = useState<string[]>([]);
  const [groupAssetsLoading, setGroupAssetsLoading] = useState(false);
  const [assetToAdd, setAssetToAdd] = useState<string | undefined>(undefined);
  const [addingAsset, setAddingAsset] = useState(false);

  const [modalVisible, setModalVisible] = useState(false);
  const [editingGroup, setEditingGroup] = useState<AssetGroupVO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const assetMap = useMemo(() => {
    const map = new Map<string, AssetVO>();
    assets.forEach((asset) => map.set(asset.id, asset));
    return map;
  }, [assets]);

  const fetchGroups = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAssetGroupList({ page: 1, page_size: 200, type: typeFilter });
      const payload = getPayload(res);
      const list: AssetGroupVO[] = payload?.content || payload?.items || [];
      setGroups(list);
      setSelectedId((prev) => {
        if (prev && list.some((group) => group.id === prev)) {
          return prev;
        }
        return list[0]?.id ?? null;
      });
    } catch (error) {
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, [typeFilter]);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  useEffect(() => {
    const fetchAssets = async () => {
      try {
        const res = await getAssetList({ page: 1, page_size: 200 });
        const payload = getPayload(res);
        setAssets(payload?.content || payload?.items || []);
      } catch (error) {
        setAssets([]);
      }
    };
    fetchAssets();
  }, []);

  const fetchGroupAssets = useCallback(async (groupId: string | null) => {
    if (!groupId) {
      setGroupAssetIds([]);
      return;
    }
    setGroupAssetsLoading(true);
    try {
      const res = await getAssetGroupAssets(groupId);
      const payload = getPayload(res);
      setGroupAssetIds(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setGroupAssetIds([]);
    } finally {
      setGroupAssetsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchGroupAssets(selectedId);
  }, [selectedId, fetchGroupAssets]);

  const selectedGroup = useMemo(() => groups.find((group) => group.id === selectedId) ?? null, [groups, selectedId]);

  const openCreate = () => {
    setEditingGroup(null);
    form.resetFields();
    form.setFieldsValue({ type: '客户' });
    setModalVisible(true);
  };

  const openEdit = (group: AssetGroupVO) => {
    setEditingGroup(group);
    form.resetFields();
    form.setFieldsValue({ name: group.name, type: group.type, description: group.description });
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingGroup) {
        await updateAssetGroup(editingGroup.id, {
          name: values.name,
          type: editingGroup.type,
          description: values.description,
        });
        message.success('分组更新成功');
      } else {
        await createAssetGroup({ name: values.name, type: values.type, description: values.description });
        message.success('分组创建成功');
      }
      setModalVisible(false);
      await fetchGroups();
    } catch (error) {
      // 表单校验失败或接口报错（拦截器已提示）
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = (group: AssetGroupVO) => {
    Modal.confirm({
      title: '确认删除分组',
      content: `确定删除分组「${group.name}」吗？组内资产不会被删除，只解除分组关系。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteAssetGroup(group.id);
          message.success('分组已删除');
          await fetchGroups();
        } catch (error) {
          // 拦截器已提示
        }
      },
    });
  };

  const handleAddAsset = async () => {
    if (!selectedGroup) {
      message.warning('请先选择分组');
      return;
    }
    if (!assetToAdd) {
      message.warning('请选择要加入的资产');
      return;
    }
    setAddingAsset(true);
    try {
      await addAssetToGroup(selectedGroup.id, assetToAdd);
      message.success('资产已加入分组');
      setAssetToAdd(undefined);
      await fetchGroupAssets(selectedGroup.id);
    } catch (error) {
      // 拦截器已提示（如「资产已在分组中」）
    } finally {
      setAddingAsset(false);
    }
  };

  const handleRemoveAsset = async (assetId: string) => {
    if (!selectedGroup) {
      return;
    }
    try {
      await removeAssetFromGroup(selectedGroup.id, assetId);
      message.success('资产已从分组移除');
      await fetchGroupAssets(selectedGroup.id);
    } catch (error) {
      // 拦截器已提示
    }
  };

  const groupAssetRows = groupAssetIds.map((assetId) => {
    const asset = assetMap.get(assetId);
    return {
      assetId,
      name: asset?.name ?? `${assetId.slice(0, 8)}…`,
      code: asset?.code ?? '-',
      targetType: asset?.targetType ?? asset?.target_type ?? '-',
      teamName: asset?.teamName ?? asset?.team_name ?? '-',
    };
  });

  return (
    <Card
      title="资产分组"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="按类型筛选"
            style={{ width: 140 }}
            value={typeFilter}
            options={GROUP_TYPE_OPTIONS.map((item) => ({ value: item, label: item }))}
            onChange={(value) => setTypeFilter(value)}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchGroups} loading={loading}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建分组
          </Button>
        </Space>
      }
    >
      <Row gutter={24}>
        <Col xs={24} lg={11}>
          <Card size="small" title="分组列表" bodyStyle={{ padding: 0 }}>
            <Table
              size="small"
              rowKey="id"
              loading={loading}
              dataSource={groups}
              pagination={false}
              locale={{ emptyText: '暂无分组，点击「新建分组」按客户/项目归类资产' }}
              rowClassName={(record) => (record.id === selectedId ? 'ant-table-row-selected' : '')}
              onRow={(record) => ({
                onClick: () => setSelectedId(record.id),
                style: { cursor: 'pointer' },
              })}
              columns={[
                { title: '名称', dataIndex: 'name', key: 'name' },
                {
                  title: '类型',
                  dataIndex: 'type',
                  key: 'type',
                  width: 90,
                  render: (value: string) => <Tag color={GROUP_TYPE_COLORS[value] ?? 'default'}>{value || '-'}</Tag>,
                },
                {
                  title: '描述',
                  dataIndex: 'description',
                  key: 'description',
                  ellipsis: true,
                  render: (value?: string) => value || '-',
                },
                {
                  title: '操作',
                  key: 'action',
                  width: 130,
                  render: (_: unknown, record: AssetGroupVO) => (
                    <Space size="small">
                      <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
                      <Typography.Link type="danger" onClick={() => handleDelete(record)}>
                        删除
                      </Typography.Link>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={13}>
          <Card
            size="small"
            title={selectedGroup ? `组内资产：${selectedGroup.name}` : '组内资产'}
            extra={
              selectedGroup ? (
                <Tag color={GROUP_TYPE_COLORS[selectedGroup.type] ?? 'default'}>{selectedGroup.type}</Tag>
              ) : null
            }
          >
            {selectedGroup ? (
              <Spin spinning={groupAssetsLoading}>
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Descriptions
                    size="small"
                    column={1}
                    items={[
                      { key: 'desc', label: '分组描述', children: selectedGroup.description || '-' },
                      {
                        key: 'created',
                        label: '创建时间',
                        children: selectedGroup.createdAt || selectedGroup.created_at || '-',
                      },
                      { key: 'count', label: '资产数量', children: groupAssetIds.length },
                    ]}
                  />
                  <Space.Compact style={{ width: '100%' }}>
                    <Select
                      showSearch
                      allowClear
                      optionFilterProp="label"
                      style={{ flex: 1 }}
                      placeholder="选择要加入本组的资产"
                      value={assetToAdd}
                      onChange={(value) => setAssetToAdd(value)}
                      options={assets
                        .filter((asset) => !groupAssetIds.includes(asset.id))
                        .map((asset) => ({ value: asset.id, label: `${asset.name}（${asset.code}）` }))}
                    />
                    <Button type="primary" loading={addingAsset} onClick={handleAddAsset}>
                      加入分组
                    </Button>
                  </Space.Compact>
                  <Table
                    size="small"
                    rowKey="assetId"
                    dataSource={groupAssetRows}
                    pagination={false}
                    locale={{ emptyText: '该分组暂无资产' }}
                    columns={[
                      { title: '资产名称', dataIndex: 'name', key: 'name' },
                      { title: '编号', dataIndex: 'code', key: 'code', width: 170 },
                      { title: '目标类型', dataIndex: 'targetType', key: 'targetType', width: 110 },
                      { title: '所属团队', dataIndex: 'teamName', key: 'teamName', width: 140 },
                      {
                        title: '操作',
                        key: 'action',
                        width: 90,
                        render: (_: unknown, record: { assetId: string }) => (
                          <Popconfirm
                            title="从分组移除该资产？"
                            onConfirm={() => handleRemoveAsset(record.assetId)}
                            okText="移除"
                            cancelText="取消"
                          >
                            <Typography.Link type="danger">移除</Typography.Link>
                          </Popconfirm>
                        ),
                      },
                    ]}
                  />
                </Space>
              </Spin>
            ) : (
              <Empty description="请选择左侧分组" />
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title={editingGroup ? '编辑分组' : '新建分组'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="分组名称" rules={[{ required: true, message: '请输入分组名称' }]}>
            <Input placeholder="例如：某客户-2026 年委托项目" maxLength={100} />
          </Form.Item>
          <Form.Item name="type" label="分组类型" rules={[{ required: true, message: '请选择或输入分组类型' }]}>
            <AutoComplete
              placeholder="客户 / 项目 / 自建"
              options={GROUP_TYPE_OPTIONS.map((item) => ({ value: item }))}
              filterOption={(input, option) => (option?.value ?? '').includes(input)}
            />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AssetGroups;
