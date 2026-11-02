import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canManageAssetGroups } from '@/utils/accessRoles';
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
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

const { TextArea } = Input;

const GROUP_TYPE_OPTIONS = ['客户', '项目', '自建'];

const GROUP_TYPE_COLORS: Record<string, string> = {
  客户: 'blue',
  项目: 'purple',
  自建: 'default',
};

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

/** 分组类型是后端数据（中文枚举值），展示层走 key 映射，值本身不翻译 */
const GROUP_TYPE_KEYS: Record<string, MessageKey> = {
  客户: 'assetGroup.typeCustomer',
  项目: 'assetGroup.typeProject',
  自建: 'assetGroup.typeCustom',
};

const AssetGroups: React.FC = () => {
  const { t } = useT();
  const groupTypeLabel = (value?: string | null) => {
    if (!value) return '-';
    const key = GROUP_TYPE_KEYS[value];
    return key ? t(key) : value;
  };
  const [groups, setGroups] = useState<AssetGroupVO[]>([]);
  // t49：分组写（创建/编辑/删除/加移资产）仅 admin/PL/TL，工程师只读
  const { profile: groupProfile, loaded: groupProfileLoaded } = useAccessProfile();
  const allowManageGroups = groupProfileLoaded && canManageAssetGroups(groupProfile);
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
        message.success(t('assetGroup.updateSuccess'));
      } else {
        await createAssetGroup({ name: values.name, type: values.type, description: values.description });
        message.success(t('assetGroup.createSuccess'));
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
      title: t('assetGroup.deleteTitle'),
      content: t('assetGroup.deleteContent', { name: group.name }),
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteAssetGroup(group.id);
          message.success(t('assetGroup.deleteSuccess'));
          await fetchGroups();
        } catch (error) {
          // 拦截器已提示
        }
      },
    });
  };

  const handleAddAsset = async () => {
    if (!selectedGroup) {
      message.warning(t('assetGroup.selectGroupFirst'));
      return;
    }
    if (!assetToAdd) {
      message.warning(t('assetGroup.selectAssetFirst'));
      return;
    }
    setAddingAsset(true);
    try {
      await addAssetToGroup(selectedGroup.id, assetToAdd);
      message.success(t('assetGroup.addAssetSuccess'));
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
      message.success(t('assetGroup.removeAssetSuccess'));
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
      title={t('assetGroup.title')}
      extra={
        <Space>
          <Select
            allowClear
            placeholder={t('assetGroup.filterType')}
            style={{ width: 140 }}
            value={typeFilter}
            options={GROUP_TYPE_OPTIONS.map((item) => ({ value: item, label: groupTypeLabel(item) }))}
            onChange={(value) => setTypeFilter(value)}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchGroups} loading={loading}>{t('common.refresh')}</Button>
          {allowManageGroups ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>{t('assetGroup.new')}</Button>
          ) : null}
        </Space>
      }
    >
      <Row gutter={24}>
        <Col xs={24} lg={11}>
          <Card size="small" title={t('assetGroup.listTitle')} bodyStyle={{ padding: 0 }}>
            <Table
              size="small"
              rowKey="id"
              loading={loading}
              dataSource={groups}
              pagination={false}
              locale={{ emptyText: t('assetGroup.empty') }}
              rowClassName={(record) => (record.id === selectedId ? 'ant-table-row-selected' : '')}
              onRow={(record) => ({
                onClick: () => setSelectedId(record.id),
                style: { cursor: 'pointer' },
              })}
              columns={[
                { title: t('assetGroup.colName'), dataIndex: 'name', key: 'name' },
                {
                  title: t('assetGroup.colType'),
                  dataIndex: 'type',
                  key: 'type',
                  width: 90,
                  render: (value: string) => <Tag color={GROUP_TYPE_COLORS[value] ?? 'default'}>{groupTypeLabel(value)}</Tag>,
                },
                {
                  title: t('assetGroup.colDescription'),
                  dataIndex: 'description',
                  key: 'description',
                  ellipsis: true,
                  render: (value?: string) => value || '-',
                },
                {
                  title: t('assetGroup.colAction'),
                  key: 'action',
                  width: 130,
                  render: (_: unknown, record: AssetGroupVO) => (
                    <Space size="small">
                      {allowManageGroups ? (
                        <>
                          <Typography.Link onClick={() => openEdit(record)}>{t('common.edit')}</Typography.Link>
                          <Typography.Link type="danger" onClick={() => handleDelete(record)}>{t('common.delete')}</Typography.Link>
                        </>
                      ) : (
                        <Typography.Text type="secondary">{t('assetGroup.readonly')}</Typography.Text>
                      )}
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
            title={selectedGroup ? t('assetGroup.assetsTitleWithName', { name: selectedGroup.name }) : t('assetGroup.assetsTitle')}
            extra={
              selectedGroup ? (
                <Tag color={GROUP_TYPE_COLORS[selectedGroup.type] ?? 'default'}>{groupTypeLabel(selectedGroup.type)}</Tag>
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
                      { key: 'desc', label: t('assetGroup.descLabel'), children: selectedGroup.description || '-' },
                      {
                        key: 'created',
                        label: t('assetGroup.createdAt'),
                        children: selectedGroup.createdAt || selectedGroup.created_at || '-',
                      },
                      { key: 'count', label: t('assetGroup.assetCount'), children: groupAssetIds.length },
                    ]}
                  />
                  {allowManageGroups ? (
                    <Space.Compact style={{ width: '100%' }}>
                      <Select
                        showSearch
                        allowClear
                        optionFilterProp="label"
                        style={{ flex: 1 }}
                        placeholder={t('assetGroup.addAssetPlaceholder')}
                        value={assetToAdd}
                        onChange={(value) => setAssetToAdd(value)}
                        options={assets
                          .filter((asset) => !groupAssetIds.includes(asset.id))
                          .map((asset) => ({ value: asset.id, label: `${asset.name}（${asset.code}）` }))}
                      />
                      <Button type="primary" loading={addingAsset} onClick={handleAddAsset}>{t('assetGroup.addAsset')}</Button>
                    </Space.Compact>
                  ) : (
                    <Typography.Text type="secondary">{t('assetGroup.readonlyHint')}</Typography.Text>
                  )}
                  <Table
                    size="small"
                    rowKey="assetId"
                    dataSource={groupAssetRows}
                    pagination={false}
                    locale={{ emptyText: t('assetGroup.emptyAssets') }}
                    columns={[
                      { title: t('assetGroup.colAssetName'), dataIndex: 'name', key: 'name' },
                      { title: t('assetGroup.colCode'), dataIndex: 'code', key: 'code', width: 170 },
                      { title: t('assetGroup.colTargetType'), dataIndex: 'targetType', key: 'targetType', width: 110 },
                      { title: t('assetGroup.colTeam'), dataIndex: 'teamName', key: 'teamName', width: 140 },
                      {
                        title: t('assetGroup.colAction'),
                        key: 'action',
                        width: 90,
                        render: (_: unknown, record: { assetId: string }) =>
                          allowManageGroups ? (
                            <Popconfirm
                              title={t('assetGroup.removeAssetConfirm')}
                              onConfirm={() => handleRemoveAsset(record.assetId)}
                              okText={t('assetGroup.remove')}
                              cancelText={t('common.cancel')}
                            >
                              <Typography.Link type="danger">{t('assetGroup.remove')}</Typography.Link>
                            </Popconfirm>
                          ) : (
                            <Typography.Text type="secondary">-</Typography.Text>
                          ),
                      },
                    ]}
                  />
                </Space>
              </Spin>
            ) : (
              <Empty description={t('assetGroup.selectGroup')} />
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title={editingGroup ? t('assetGroup.modalEdit') : t('assetGroup.modalCreate')}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('assetGroup.formName')} rules={[{ required: true, message: t('assetGroup.formNameRequired') }]}>
            <Input placeholder={t('assetGroup.formNamePlaceholder')} maxLength={100} />
          </Form.Item>
          <Form.Item name="type" label={t('assetGroup.formType')} rules={[{ required: true, message: t('assetGroup.formTypeRequired') }]}>
            <AutoComplete
              placeholder={t('assetGroup.formTypePlaceholder')}
              options={GROUP_TYPE_OPTIONS.map((item) => ({ value: item }))}
              filterOption={(input, option) => (option?.value ?? '').includes(input)}
            />
          </Form.Item>
          <Form.Item name="description" label={t('assetGroup.colDescription')}>
            <TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AssetGroups;
