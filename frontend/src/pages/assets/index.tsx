import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Input,
  Tag,
  Card,
  Modal,
  Form,
  Select,
  message,
} from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  getAssetList,
  createAsset,
  updateAsset,
  deleteAsset,
  getAssetOptions,
  createAssetAccessRequest,
} from '@/api/asset';
import type {
  AssetVO,
  CreateAssetRequest,
  UpdateAssetRequest,
} from '@/types/asset';
import { getTeamList } from '@/api/team';
import { useAccessProfile } from '@/hooks/useAccessProfile';
import { canCreateAsset } from '@/utils/accessRoles';
import type { TeamVO } from '@/types/team';
import { useT } from '@/i18n';
import type { MessageKey } from '@/i18n/messages/zh-CN';

const { TextArea } = Input;

const Assets: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  // t49 起 POST /assets 仅 admin/project_leader/team_leader：工程师不渲染新建入口
  const { profile, loaded: profileLoaded } = useAccessProfile();
  const allowCreateAsset = profileLoaded && canCreateAsset(profile);
  const [data, setData] = useState<AssetVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentAsset, setCurrentAsset] = useState<AssetVO | null>(null);
  const [teams, setTeams] = useState<TeamVO[]>([]);
  const [targetTypeOptions, setTargetTypeOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [targetSourceOptions, setTargetSourceOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [priorityOptions, setPriorityOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [statusOptions, setStatusOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [form] = Form.useForm();

  const unwrapPayload = (res: any) => (res?.data ? res.data : res);
  const normalizeAsset = (item: AssetVO): AssetVO => ({
    ...item,
    team_id: item.team_id ?? item.teamId,
    team_name: item.team_name ?? item.teamName,
    owner_id: item.owner_id ?? item.ownerId,
    owner_name: item.owner_name ?? item.ownerName,
    target_type: item.target_type ?? item.targetType ?? '',
    target_source: item.target_source ?? item.targetSource ?? '',
    created_at: item.created_at ?? item.createdAt ?? '',
    updated_at: item.updated_at ?? item.updatedAt,
  });

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getAssetList(params);
      const payload = unwrapPayload(res);
      const items = payload.content || payload.items || [];
      const totalCount = payload.totalElements || payload.total || 0;
      setData(items.map((item: AssetVO) => normalizeAsset(item)));
      setTotal(totalCount);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const fetchTeams = async () => {
    try {
      const res = await getTeamList();
      const payload = unwrapPayload(res);
      const items = payload.content || payload.items || [];
      setTeams(Array.isArray(items) ? items : []);
    } catch (error) {
      setTeams([]);
    }
  };

  const fetchAssetOptions = async () => {
    try {
      const res: any = await getAssetOptions();
      const payload = res?.data?.data ?? res?.data ?? res;
      setTargetTypeOptions(Array.isArray(payload?.targetTypes) ? payload.targetTypes : []);
      setTargetSourceOptions(Array.isArray(payload?.targetSources) ? payload.targetSources : []);
      setPriorityOptions(Array.isArray(payload?.priorities) ? payload.priorities : []);
      setStatusOptions(Array.isArray(payload?.statuses) ? payload.statuses : []);
    } catch (error) {
      setTargetTypeOptions([]);
      setTargetSourceOptions([]);
      setPriorityOptions([]);
      setStatusOptions([]);
    }
  };

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  useEffect(() => {
    fetchTeams();
    fetchAssetOptions();
  }, []);

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    form.setFieldsValue({
      priority: priorityOptions[0]?.value,
      status: statusOptions[0]?.value,
    });
    setCurrentAsset(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: AssetVO) => {
    setModalMode('edit');
    setCurrentAsset(record);
    form.setFieldsValue({
      name: record.name,
      team_id: record.team_id || record.teamId,
      target_type: record.target_type,
      target_source: record.target_source,
      description: record.description,
      priority: record.priority,
      status: record.status,
    });
    setIsModalVisible(true);
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('common.deleteConfirmTitle'),
      content: t('asset.list.deleteContent'),
      onOk: async () => {
        try {
          await deleteAsset(id);
          message.success(t('asset.list.deleteSuccess'));
          fetchData();
        } catch (error) {
          // Error handled by interceptor
        }
      }
    });
  };

  /**
   * 在列表页直接发起访问申请。
   *
   * 说明：未获授权的用户打不开资产详情页（详情接口会返回 403），
   * 因此申请入口必须同时存在于列表页，否则工程师无法走「申请 → 审批 → 可见」流程。
   */
  const handleRequestAccess = (record: AssetVO) => {
    let canViewFiles = true;
    Modal.confirm({
      title: t('asset.request.title', { name: record.name }),
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8, color: '#888' }}>{t('asset.request.tip')}</div>
          <Select
            defaultValue={true}
            style={{ width: '100%' }}
            onChange={(value) => { canViewFiles = value; }}
            options={[
              { value: true, label: t('asset.request.needFiles') },
              { value: false, label: t('asset.request.basicOnly') },
            ]}
          />
        </div>
      ),
      okText: t('asset.request.submit'),
      onOk: async () => {
        try {
          await createAssetAccessRequest(record.id, {
            can_view_files: canViewFiles,
            request_note: t('asset.request.note'),
          });
          message.success(t('asset.request.success'));
        } catch (error: any) {
          message.error(error?.response?.data?.message || t('asset.request.failed'));
        }
      }
    });
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (modalMode === 'create') {
        await createAsset(values as CreateAssetRequest);
        message.success(t('asset.list.createSuccess'));
      } else {
        if (currentAsset) {
          await updateAsset(currentAsset.id, values as UpdateAssetRequest);
          message.success(t('asset.list.updateSuccess'));
        }
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error) {
      // Form validation error or API error
    }
  };

  const columns = [
    { 
      title: t('asset.list.colName'), 
      dataIndex: 'name', 
      key: 'name',
      render: (text: string, record: AssetVO) => (
        <a onClick={() => navigate(`/assets/${record.id}`)} style={{ fontWeight: 500 }}>{text}</a>
      )
    },
    { title: t('asset.list.colCode'), dataIndex: 'code', key: 'code' },
    { 
      title: t('asset.list.colTeam'), 
      dataIndex: 'team_name', 
      key: 'team_name',
      render: (text: string) => text || '-'
    },
    { 
      title: t('asset.list.colOwner'), 
      dataIndex: 'owner_name', 
      key: 'owner_name',
      render: (text: string) => text || '-'
    },
    { title: t('asset.list.colType'), dataIndex: 'target_type', key: 'target_type' },
    { title: t('asset.list.colSource'), dataIndex: 'target_source', key: 'target_source' },
    { 
      title: t('asset.list.colPriority'), 
      dataIndex: 'priority', 
      key: 'priority',
      render: (text: string) => {
        const colors: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
        return <Tag color={colors[text] || 'default'}>{text}</Tag>;
      }
    },
    { 
      title: t('asset.list.colStatus'), 
      dataIndex: 'status', 
      key: 'status',
      render: (text: string) => {
        const colors: Record<string, string> = {
          active: 'success',
          under_test: 'processing',
          suspended: 'warning',
          archived: 'default',
        };
        const labels: Record<string, MessageKey> = {
          active: 'asset.status.active',
          under_test: 'asset.status.underTest',
          suspended: 'asset.status.suspended',
          archived: 'asset.status.archived',
        };
        return <Tag color={colors[text] || 'default'}>{labels[text] ? t(labels[text]) : text}</Tag>;
      }
    },
    {
      title: t('asset.list.colAction'),
      key: 'action',
      render: (_: any, record: AssetVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/assets/${record.id}`)}>{t('asset.list.enter')}</a>
          <a onClick={() => handleRequestAccess(record)}>{t('asset.list.requestAccess')}</a>
          <a onClick={() => handleEdit(record)}>{t('asset.list.editAsset')}</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record.id)}>{t('asset.list.deleteAsset')}</a>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('asset.list.title')}
      extra={
        allowCreateAsset ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>{t('asset.list.new')}</Button>
        ) : null
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input 
          placeholder={t('asset.list.searchPlaceholder')} 
          prefix={<SearchOutlined />} 
          style={{ width: 300 }} 
          onPressEnter={(e) => setParams({ ...params, search: e.currentTarget.value, page: 1 })}
        />
      </div>
      <Table 
        columns={columns} 
        dataSource={data} 
        rowKey="id"
        loading={loading}
        pagination={{
          current: params.page,
          pageSize: params.page_size,
          total,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize })
        }}
      />

      <Modal
        title={modalMode === 'create' ? t('asset.form.createTitle') : t('asset.form.editTitle')}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('asset.list.colName')} rules={[{ required: true, message: t('asset.form.nameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="team_id" label={t('asset.list.colTeam')} rules={[{ required: true, message: t('asset.form.teamRequired') }]}>
            <Select placeholder={t('asset.form.teamRequired')} showSearch optionFilterProp="label" options={teams.map((team) => ({ label: team.name, value: team.id }))} />
          </Form.Item>
          <Form.Item name="target_type" label={t('asset.form.targetType')} rules={[{ required: true, message: t('asset.form.targetTypeRequired') }]}>
            <Select
              placeholder={t('asset.form.targetTypeRequired')}
              options={targetTypeOptions}
              disabled={targetTypeOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="target_source" label={t('asset.form.targetSource')} rules={[{ required: true, message: t('asset.form.targetSourceRequired') }]}>
            <Select
              placeholder={t('asset.form.targetSourceRequired')}
              options={targetSourceOptions}
              disabled={targetSourceOptions.length === 0}
            />
          </Form.Item>
          <Form.Item name="priority" label={t('asset.list.colPriority')} rules={[{ required: true, message: t('asset.form.priorityRequired') }]}>
            <Select placeholder={t('asset.form.priorityRequired')} options={priorityOptions} />
          </Form.Item>
          <Form.Item name="status" label={t('asset.list.colStatus')} rules={[{ required: true, message: t('asset.form.statusRequired') }]}>
            <Select placeholder={t('asset.form.statusRequired')} options={statusOptions} />
          </Form.Item>
          <Form.Item name="description" label={t('asset.form.description')}>
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default Assets;
