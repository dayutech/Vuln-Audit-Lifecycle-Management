import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import {
  assignUserRole,
  createUser,
  deleteUser,
  getUserList,
  getUserRoles,
  resetUserPassword,
  revokeUserRole,
  updateUser,
  updateUserStatus,
} from '@/api/user';
import { extractRoleList, getRoleList } from '@/api/role';
import type { UserStatus, UserVO } from '@/types/user';
import type { RoleVO } from '@/types/role';
import { isSystemRole } from '@/types/role';
import { formatDateTime } from '@/utils/datetime';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

const { Text } = Typography;

const statusMeta: Record<string, { color: string; label: string }> = {
  active: { color: 'success', label: '活跃' },
  suspended: { color: 'warning', label: '已停用' },
  deactivated: { color: 'default', label: '已注销' },
};

/**
 * 账号管理（/users）
 *
 * 已接入的后端能力：
 * - 用户 CRUD：GET/POST/PUT/DELETE /users
 * - 角色分配：GET /users/{id}/roles、POST /users/{id}/roles?roleId=、DELETE /users/{id}/roles/{roleId}
 * - 启用/停用：PATCH /users/{id}/status?status=active|suspended|deactivated
 * - 重置密码：POST /users/{id}/reset-password?newPassword=***
 *
 * 幂等与存在性（后端语义）：重复分配角色返回 200 且不重复插入；用户/角色不存在返回 404。
 * 页面显式区分这两种结果，不做静默吞掉。
 */
const Users: React.FC = () => {
  const [data, setData] = useState<UserVO[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [params, setParams] = useState({ page: 1, page_size: 10, search: '' });
  /**
   * 403 可读提示：t45 起 GET /users 仅 admin / project_leader 可用。
   * 菜单入口已对非管理层隐藏，这里再兜住「直接输 URL / 权限画像未返回时已进入页面」的情况。
   */
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  // 用户新增/编辑
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentUser, setCurrentUser] = useState<UserVO | null>(null);
  const [form] = Form.useForm();

  // 角色分配抽屉
  const [roleDrawerOpen, setRoleDrawerOpen] = useState(false);
  const [roleUser, setRoleUser] = useState<UserVO | null>(null);
  const [userRoles, setUserRoles] = useState<RoleVO[]>([]);
  const [userRoleLoading, setUserRoleLoading] = useState(false);
  const [selectedRoleId, setSelectedRoleId] = useState<string | undefined>(undefined);
  const [roleSubmitting, setRoleSubmitting] = useState(false);
  /** 角色分配失败时的行内提示（404 表示用户/角色不存在） */
  const [roleError, setRoleError] = useState<string | null>(null);

  // 重置密码
  const [resetUser, setResetUser] = useState<UserVO | null>(null);
  const [resetForm] = Form.useForm();
  const [resetSubmitting, setResetSubmitting] = useState(false);

  const fetchRoles = async () => {
    try {
      const res = await getRoleList();
      setRoles(extractRoleList(res));
    } catch {
      setRoles([]);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res: any = await getUserList(params);
      const payload = res?.data ?? res ?? {};
      const items = payload.content || payload.items || [];
      setData(items);
      setTotal(payload.totalElements || payload.total || 0);
      setAccessDenied(null);
    } catch (error) {
      // 403 不是「加载失败」，而是该账号无权查看用户管理：给可读提示而不是空表格
      if (getApiErrorStatus(error) === 403) {
        setAccessDenied(
          getApiErrorMessage(error, '账号管理仅管理员 / 项目负责人可用，当前账号无权查看用户列表'),
        );
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
  }, []);

  useEffect(() => {
    fetchData();
  }, [params.page, params.page_size, params.search]);

  const displayName = (user: UserVO) => user.fullName || user.full_name || user.username;

  const assignedRoleIds = useMemo(() => new Set(userRoles.map((role) => role.id)), [userRoles]);
  const assignableRoles = useMemo(
    () => roles.filter((role) => !assignedRoleIds.has(role.id)),
    [roles, assignedRoleIds],
  );

  // ==================== 用户新增/编辑 ====================

  const handleCreate = () => {
    setModalMode('create');
    form.resetFields();
    setCurrentUser(null);
    setIsModalVisible(true);
  };

  const handleEdit = (record: UserVO) => {
    setModalMode('edit');
    setCurrentUser(record);
    form.setFieldsValue({
      fullName: record.fullName ?? record.full_name,
      email: record.email,
      phone: record.phone,
      position: record.position,
    });
    setIsModalVisible(true);
  };

  const handleModalOk = async () => {
    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    try {
      if (modalMode === 'create') {
        await createUser({
          username: values.username?.trim(),
          password: values.password,
          email: values.email,
          phone: values.phone,
          fullName: values.fullName,
          position: values.position,
        });
        message.success('用户创建成功，可在「角色」中为其分配角色');
      } else if (currentUser) {
        await updateUser(currentUser.id, {
          fullName: values.fullName,
          email: values.email,
          phone: values.phone,
          position: values.position,
        });
        message.success('更新成功');
      }
      setIsModalVisible(false);
      fetchData();
    } catch {
      // 400/409 等由拦截器提示
    }
  };

  const handleDelete = (record: UserVO) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除用户 [${record.username}] 吗？删除为软删除（状态置为 deactivated）。`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteUser(record.id);
          message.success('删除成功');
          fetchData();
        } catch {
          // 400（不能删除自己）/404 由拦截器提示
        }
      },
    });
  };

  // ==================== 启用 / 停用 ====================

  const handleToggleStatus = (record: UserVO) => {
    const nextStatus: UserStatus = record.status === 'active' ? 'suspended' : 'active';
    const actionLabel = nextStatus === 'suspended' ? '停用' : '启用';
    Modal.confirm({
      title: `确认${actionLabel}`,
      content: `确定要${actionLabel}用户 [${record.username}] 吗？`,
      onOk: async () => {
        try {
          const res: any = await updateUserStatus(record.id, nextStatus);
          const updated = res?.data ?? res;
          message.success(
            `${actionLabel}成功，当前状态：${statusMeta[updated?.status ?? nextStatus]?.label ?? nextStatus}`,
          );
          fetchData();
        } catch {
          // 404（用户不存在）由拦截器提示
        }
      },
    });
  };

  // ==================== 重置密码 ====================

  const openResetPassword = (record: UserVO) => {
    setResetUser(record);
    resetForm.resetFields();
  };

  const handleResetPassword = async () => {
    if (!resetUser) return;
    let values: any;
    try {
      values = await resetForm.validateFields();
    } catch {
      return;
    }
    setResetSubmitting(true);
    try {
      await resetUserPassword(resetUser.id, values.newPassword);
      message.success(`已重置 [${resetUser.username}] 的密码`);
      setResetUser(null);
    } catch {
      // 404 由拦截器提示
    } finally {
      setResetSubmitting(false);
    }
  };

  // ==================== 角色分配 ====================

  const openRoleDrawer = async (record: UserVO) => {
    setRoleUser(record);
    setSelectedRoleId(undefined);
    setRoleError(null);
    setRoleDrawerOpen(true);
    await loadUserRoles(record.id);
  };

  const loadUserRoles = async (userId: string) => {
    setUserRoleLoading(true);
    try {
      const res: any = await getUserRoles(userId);
      const payload = res?.data ?? res;
      setUserRoles(Array.isArray(payload) ? payload : []);
    } catch {
      setUserRoles([]);
    } finally {
      setUserRoleLoading(false);
    }
  };

  const handleAssignRole = async () => {
    if (!roleUser || !selectedRoleId) {
      message.warning('请先选择要分配的角色');
      return;
    }
    const role = roles.find((item) => item.id === selectedRoleId);
    const alreadyAssigned = assignedRoleIds.has(selectedRoleId);
    setRoleSubmitting(true);
    setRoleError(null);
    try {
      await assignUserRole(roleUser.id, selectedRoleId);
      if (alreadyAssigned) {
        message.info(`该用户已拥有「${role?.name ?? selectedRoleId}」，后端按幂等处理（200，不重复插入）`);
      } else {
        message.success(`已分配角色「${role?.name ?? selectedRoleId}」`);
      }
      setSelectedRoleId(undefined);
      await loadUserRoles(roleUser.id);
      fetchData();
    } catch (error: any) {
      const status = getApiErrorStatus(error);
      const message0 = getApiErrorMessage(error, '分配角色失败');
      setRoleError(
        status === 404
          ? `${message0}（404：用户或角色不存在，请刷新列表后重试）`
          : `${message0}${status ? `（HTTP ${status}）` : ''}`,
      );
    } finally {
      setRoleSubmitting(false);
    }
  };

  const handleRevokeRole = (role: RoleVO) => {
    if (!roleUser) return;
    Modal.confirm({
      title: '移除角色',
      content: `确定要移除「${role.name}」吗？用户将立即失去该角色对应的权限点。`,
      okButtonProps: { danger: true },
      onOk: async () => {
        setRoleError(null);
        try {
          await revokeUserRole(roleUser.id, role.id);
          message.success(`已移除角色「${role.name}」`);
          await loadUserRoles(roleUser.id);
          fetchData();
        } catch (error: any) {
          const status = getApiErrorStatus(error);
          if (status === 404) {
            message.info('该用户未拥有此角色（后端返回 404），已按已移除处理');
            await loadUserRoles(roleUser.id);
            return;
          }
          setRoleError(`${getApiErrorMessage(error, '移除角色失败')}${status ? `（HTTP ${status}）` : ''}`);
        }
      },
    });
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    {
      title: '姓名',
      key: 'fullName',
      render: (_: unknown, record: UserVO) => displayName(record) || <Text type="secondary">-</Text>,
    },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const meta = statusMeta[status] ?? { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '最后登录',
      key: 'lastLogin',
      render: (_: unknown, record: UserVO) => formatDateTime(record.lastLogin ?? record.last_login),
    },
    {
      title: '创建时间',
      key: 'createdAt',
      render: (_: unknown, record: UserVO) => formatDateTime(record.createdAt ?? record.created_at),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: UserVO) => (
        <Space size="middle" wrap>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <a onClick={() => openRoleDrawer(record)}>
            <TeamOutlined /> 角色
          </a>
          <a onClick={() => handleToggleStatus(record)}>
            {record.status === 'active' ? '停用' : '启用'}
          </a>
          <a onClick={() => openResetPassword(record)}>重置密码</a>
          <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
        </Space>
      ),
    },
  ];

  if (accessDenied) {
    return (
      <Card title="账号管理">
        <Alert
          type="warning"
          showIcon
          message="当前账号无权查看用户管理"
          description={
            <div>
              <p style={{ marginBottom: 8 }}>{accessDenied}</p>
              <p style={{ margin: 0 }}>
                账号管理（含用户列表、角色分配、重置密码）仅对管理员 / 项目负责人开放；
                人员下拉与作者名展示请使用其他页面的用户目录数据。
              </p>
            </div>
          }
        />
      </Card>
    );
  }

  return (
    <Card
      title="账号管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchData}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>创建用户</Button>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder="搜索用户名/姓名/邮箱，回车检索"
          prefix={<SearchOutlined />}
          style={{ width: 320 }}
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => setParams({ ...params, search: keyword, page: 1 })}
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
          showSizeChanger: true,
          onChange: (page, pageSize) => setParams({ ...params, page, page_size: pageSize }),
        }}
      />

      {/* 用户新增 / 编辑 */}
      <Modal
        title={modalMode === 'create' ? '创建用户' : '编辑用户'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {modalMode === 'create' && (
            <>
              <Form.Item
                name="username"
                label="用户名"
                rules={[
                  { required: true, message: '请输入用户名' },
                  { min: 3, max: 50, message: '用户名长度需在 3-50 个字符之间' },
                ]}
              >
                <Input autoComplete="off" />
              </Form.Item>
              <Form.Item
                name="password"
                label="初始密码"
                rules={[
                  { required: true, message: '请输入初始密码' },
                  { min: 6, max: 100, message: '密码长度需在 6-100 个字符之间' },
                ]}
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </>
          )}
          <Form.Item name="fullName" label="姓名">
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ required: true, type: 'email', message: '请输入正确的邮箱格式' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="position" label="职位">
            <Input maxLength={100} />
          </Form.Item>
        </Form>
        {modalMode === 'create' && (
          <Alert
            type="info"
            showIcon
            message="创建后请在列表点击「角色」为该账号分配角色，否则账号登录后没有任何角色权限。"
          />
        )}
      </Modal>

      {/* 角色分配抽屉 */}
      <Drawer
        title={
          <Space>
            <UserSwitchOutlined />
            角色分配 · {roleUser ? displayName(roleUser) : ''}
          </Space>
        }
        open={roleDrawerOpen}
        onClose={() => setRoleDrawerOpen(false)}
        width={520}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="重复分配角色后端幂等返回 200；用户或角色不存在返回 404。"
        />

        {roleError && (
          <Alert
            type="error"
            showIcon
            closable
            style={{ marginBottom: 16 }}
            message="角色操作失败"
            description={roleError}
            onClose={() => setRoleError(null)}
          />
        )}

        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <Select
            style={{ flex: 1 }}
            placeholder="选择要分配的角色"
            value={selectedRoleId}
            onChange={setSelectedRoleId}
            options={roles.map((role) => ({
              value: role.id,
              label: `${role.name}（${role.code}）${isSystemRole(role) ? ' · 内置' : ''}${assignedRoleIds.has(role.id) ? ' · 已分配' : ''}`,
            }))}
            showSearch
            optionFilterProp="label"
            notFoundContent="暂无角色数据"
          />
          <Button type="primary" loading={roleSubmitting} onClick={handleAssignRole}>
            分配
          </Button>
        </Space.Compact>

        {assignableRoles.length === 0 && roles.length > 0 && (
          <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            该用户已拥有全部角色；如需验证幂等，可直接再次点击「分配」。
          </Text>
        )}

        <Table
          size="small"
          rowKey="id"
          loading={userRoleLoading}
          dataSource={userRoles}
          pagination={false}
          locale={{ emptyText: '该用户暂无角色' }}
          columns={[
            { title: '角色名称', dataIndex: 'name', key: 'name' },
            {
              title: '编码',
              dataIndex: 'code',
              key: 'code',
              render: (code: string) => <Text code>{code}</Text>,
            },
            {
              title: '类型',
              key: 'isSystem',
              render: (_: unknown, role: RoleVO) =>
                isSystemRole(role) ? <Tag color="gold">内置</Tag> : <Tag color="green">自定义</Tag>,
            },
            {
              title: '操作',
              key: 'action',
              render: (_: unknown, role: RoleVO) => (
                <Tooltip title="移除后用户立即失去该角色的权限点">
                  <a style={{ color: 'red' }} onClick={() => handleRevokeRole(role)}>移除</a>
                </Tooltip>
              ),
            },
          ]}
        />
      </Drawer>

      {/* 重置密码 */}
      <Modal
        title={`重置密码 · ${resetUser?.username ?? ''}`}
        open={!!resetUser}
        onOk={handleResetPassword}
        onCancel={() => setResetUser(null)}
        okText="重置"
        confirmLoading={resetSubmitting}
        destroyOnClose
      >
        <Form form={resetForm} layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, max: 100, message: '密码长度需在 6-100 个字符之间' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
        <Text type="secondary">重置后请通知用户使用新密码登录；该操作会记录操作审计。</Text>
      </Modal>
    </Card>
  );
};

export default Users;
