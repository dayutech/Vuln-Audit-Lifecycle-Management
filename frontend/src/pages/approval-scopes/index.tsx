import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getMyApprovalScopes,
  grantApprovalScope,
  listApprovalScopes,
  revokeApprovalScope,
} from '@/api/approvalScope';
import type { ApprovalScopeVO } from '@/api/approvalScope';
import { getAllTeams } from '@/api/team';
import { getAssetList, getAssetOptions } from '@/api/asset';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import type { TeamVO } from '@/types/team';
import type { AssetVO } from '@/types/asset';

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

const SCOPE_TYPE_OPTIONS = [
  { value: 'team', label: '团队范围' },
  { value: 'asset', label: '单个资产' },
  { value: 'asset_category', label: '资产类别' },
];

const SCOPE_TYPE_META: Record<string, { label: string; color: string }> = {
  team: { label: '团队', color: 'blue' },
  asset: { label: '资产', color: 'purple' },
  asset_category: { label: '资产类别', color: 'cyan' },
};

const userName = (user?: UserDirectoryEntry | null) =>
  user ? (user.fullName || user.username) : '';

const ApprovalScopes: React.FC = () => {
  const [scopes, setScopes] = useState<ApprovalScopeVO[]>([]);
  const [myScopes, setMyScopes] = useState<ApprovalScopeVO[]>([]);
  const [loading, setLoading] = useState(false);

  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [teams, setTeams] = useState<TeamVO[]>([]);
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [assetCategories, setAssetCategories] = useState<string[]>([]);

  const [modalVisible, setModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const scopeType = Form.useWatch('scopeType', form);

  const userMap = useMemo(() => {
    const map = new Map<string, UserDirectoryEntry>();
    users.forEach((user) => map.set(user.id, user));
    return map;
  }, [users]);

  const teamMap = useMemo(() => new Map(teams.map((team) => [team.id, team.name])), [teams]);
  const assetMap = useMemo(() => new Map(assets.map((asset) => [asset.id, asset.name])), [assets]);

  const userLabel = useCallback(
    (id?: string | null) => {
      if (!id) {
        return '-';
      }
      const user = userMap.get(id);
      return user ? `${userName(user)}${user.username ? `（${user.username}）` : ''}` : `${id.slice(0, 8)}…`;
    },
    [userMap],
  );

  const scopeTargetLabel = useCallback(
    (scope: ApprovalScopeVO) => {
      if (scope.scopeType === 'team') {
        return teamMap.get(scope.scopeId) ?? `${scope.scopeId.slice(0, 8)}…`;
      }
      if (scope.scopeType === 'asset') {
        return assetMap.get(scope.scopeId) ?? `${scope.scopeId.slice(0, 8)}…`;
      }
      return scope.scopeId;
    },
    [teamMap, assetMap],
  );

  const fetchScopes = useCallback(async () => {
    setLoading(true);
    try {
      const [allRes, myRes] = await Promise.all([listApprovalScopes(), getMyApprovalScopes()]);
      const all = getPayload(allRes);
      const mine = getPayload(myRes);
      setScopes(Array.isArray(all) ? all : []);
      setMyScopes(Array.isArray(mine) ? mine : []);
    } catch (error) {
      setScopes([]);
      setMyScopes([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchScopes();
  }, [fetchScopes]);

  useEffect(() => {
    const fetchRefs = async () => {
      try {
        const [userList, teamRes, assetRes, optionRes] = await Promise.all([
          getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT }),
          getAllTeams(),
          getAssetList({ page: 1, page_size: 200 }),
          getAssetOptions(),
        ]);
        const teamPayload = getPayload(teamRes);
        const assetPayload = getPayload(assetRes);
        const optionPayload = getPayload(optionRes) as { targetTypes?: Array<{ value: string }> } | undefined;
        setUsers(userList);
        setTeams(Array.isArray(teamPayload) ? teamPayload : []);
        setAssets(assetPayload?.content || assetPayload?.items || []);
        setAssetCategories((optionPayload?.targetTypes ?? []).map((item) => String(item.value)));
      } catch (error) {
        // 参照数据加载失败不阻塞页面
      }
    };
    fetchRefs();
  }, []);

  const openGrant = () => {
    form.resetFields();
    form.setFieldsValue({ scopeType: 'team' });
    setModalVisible(true);
  };

  const handleGrant = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await grantApprovalScope({
        approverId: values.approverId,
        scopeType: values.scopeType,
        scopeId: values.scopeId,
        expiresAt: values.expiresAt ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss') : null,
      });
      message.success('审批范围授予成功');
      setModalVisible(false);
      await fetchScopes();
    } catch (error) {
      // 表单校验失败或接口报错（拦截器已提示）
    } finally {
      setSubmitting(false);
    }
  };

  const handleRevoke = async (scope: ApprovalScopeVO) => {
    try {
      await revokeApprovalScope(scope.id);
      message.success('审批范围已撤销');
      await fetchScopes();
    } catch (error) {
      // 拦截器已提示
    }
  };

  const renderScopeIdField = () => {
    if (scopeType === 'asset') {
      return (
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择资产"
          options={assets.map((asset) => ({ value: asset.id, label: `${asset.name}（${asset.code}）` }))}
        />
      );
    }
    if (scopeType === 'asset_category') {
      return (
        <AutoComplete
          placeholder="选择或输入资产类别（目标类型）"
          options={assetCategories.map((item) => ({ value: item }))}
          filterOption={(input, option) => (option?.value ?? '').includes(input)}
        />
      );
    }
    return (
      <Select
        showSearch
        optionFilterProp="label"
        placeholder="选择团队"
        options={teams.map((team) => ({ value: team.id, label: team.name }))}
      />
    );
  };

  const columns = [
    { title: '审批人', key: 'approver', render: (_: unknown, record: ApprovalScopeVO) => userLabel(record.approverId) },
    {
      title: '范围类型',
      key: 'scopeType',
      width: 110,
      render: (_: unknown, record: ApprovalScopeVO) => {
        const meta = SCOPE_TYPE_META[record.scopeType] ?? { label: record.scopeType, color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '范围对象',
      key: 'scopeId',
      render: (_: unknown, record: ApprovalScopeVO) => scopeTargetLabel(record),
    },
    { title: '授予人', key: 'grantedBy', width: 160, render: (_: unknown, record: ApprovalScopeVO) => userLabel(record.grantedBy) },
    {
      title: '授予时间',
      key: 'grantedAt',
      width: 170,
      render: (_: unknown, record: ApprovalScopeVO) =>
        record.grantedAt ? dayjs(record.grantedAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '过期时间',
      key: 'expiresAt',
      width: 190,
      render: (_: unknown, record: ApprovalScopeVO) => {
        if (!record.expiresAt) {
          return <Typography.Text type="secondary">长期有效</Typography.Text>;
        }
        const expired = dayjs(record.expiresAt).isBefore(dayjs());
        return (
          <Space size={4}>
            <span>{dayjs(record.expiresAt).format('YYYY-MM-DD HH:mm:ss')}</span>
            {expired ? <Tag color="red">已过期</Tag> : null}
          </Space>
        );
      },
    },
    {
      title: '状态',
      key: 'valid',
      width: 90,
      render: (_: unknown, record: ApprovalScopeVO) =>
        record.valid === false ? <Tag color="red">失效</Tag> : <Tag color="green">有效</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_: unknown, record: ApprovalScopeVO) => (
        <Popconfirm title="撤销该审批范围？" onConfirm={() => handleRevoke(record)} okText="撤销" cancelText="取消">
          <Typography.Link type="danger">撤销</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title="审批范围"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchScopes} loading={loading}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openGrant}>
            授予审批范围
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="审批权限由上级领导授予：仅 ADMIN / PROJECT_LEADER 可授予或撤销；范围支持 团队（team）、单个资产（asset）、资产类别（asset_category），可设置过期时间。"
        />

        <Card size="small" title="我的审批范围">
          {myScopes.length === 0 ? (
            <Empty description="当前账号暂无被授予的审批范围（全局管理员无需授权即可审批）" />
          ) : (
            <Descriptions
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={myScopes.map((scope) => {
                const meta = SCOPE_TYPE_META[scope.scopeType] ?? { label: scope.scopeType, color: 'default' };
                return {
                  key: scope.id,
                  label: <Tag color={meta.color}>{meta.label}</Tag>,
                  children: (
                    <Space size={8}>
                      <span>{scopeTargetLabel(scope)}</span>
                      {scope.expiresAt ? (
                        <Typography.Text type="secondary">
                          至 {dayjs(scope.expiresAt).format('YYYY-MM-DD HH:mm')}
                        </Typography.Text>
                      ) : (
                        <Typography.Text type="secondary">长期有效</Typography.Text>
                      )}
                    </Space>
                  ),
                };
              })}
            />
          )}
        </Card>

        <Card size="small" title="全部审批范围">
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={scopes}
            columns={columns}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            locale={{ emptyText: '暂无审批范围授权' }}
          />
        </Card>
      </Space>

      <Modal
        title="授予审批范围"
        open={modalVisible}
        onOk={handleGrant}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="approverId" label="审批人" rules={[{ required: true, message: '请选择审批人' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择被授权的审批人"
              options={users.map((user) => ({ value: user.id, label: `${userName(user)}（${user.username}）` }))}
            />
          </Form.Item>
          <Form.Item name="scopeType" label="范围类型" rules={[{ required: true, message: '请选择范围类型' }]}>
            <Select options={SCOPE_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="scopeId" label="范围对象" rules={[{ required: true, message: '请选择范围对象' }]}>
            {renderScopeIdField()}
          </Form.Item>
          <Form.Item name="expiresAt" label="过期时间">
            <DatePicker showTime style={{ width: '100%' }} placeholder="不填表示长期有效" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default ApprovalScopes;
