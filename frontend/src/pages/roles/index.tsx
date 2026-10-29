import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Divider,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  createRole,
  deleteRole,
  extractPermissionList,
  extractRoleList,
  getPermissionList,
  getRoleList,
  getRolePermissions,
  updateRole,
} from '@/api/role';
import type { CreateRoleRequest, PermissionVO, RoleVO, UpdateRoleRequest } from '@/types/role';
import { isSystemRole } from '@/types/role';
import {
  filterPermissions,
  getPermissionModuleLabel,
  groupPermissions,
} from '@/utils/permissions';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';

const { Text } = Typography;

/**
 * 角色与权限管理（/roles）
 *
 * 数据契约（依赖后端 RBAC 权限点体系）：
 * - `GET /roles?includePermissions=true`：角色列表 + 每个角色已分配的权限点（勾选回显）
 * - `GET /permissions`：90 个权限点目录（camelCase：apiPath / httpMethod / createdAt）
 * - `POST /roles` / `PUT /roles/{id}`：带 permissionIds 即同步权限关联（覆盖式差量，幂等）
 * - `DELETE /roles/{id}`：系统内置角色后端拒绝
 *
 * 权限点为空（后端 RBAC 初始化未部署）时页面照常渲染，并提示空态；
 * 此时保存角色**不带 permissionIds**，避免把已有授权误清空。
 */
const Roles: React.FC = () => {
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [permissions, setPermissions] = useState<PermissionVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [permissionError, setPermissionError] = useState<string | null>(null);

  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [currentRole, setCurrentRole] = useState<RoleVO | null>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [permissionKeyword, setPermissionKeyword] = useState('');
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const [rolePermissionLoading, setRolePermissionLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchPermissions = async () => {
    try {
      const res = await getPermissionList();
      const items = extractPermissionList(res);
      setPermissions(items);
      setPermissionError(items.length === 0 ? '后端返回的权限点为空。' : null);
    } catch (error: any) {
      // 权限点目录查询需要 ADMIN / PROJECT_LEADER；403 时给出明确提示而不是静默空白
      setPermissions([]);
      setPermissionError(
        error?.response?.status === 403
          ? '当前账号无权查看权限点目录（需要 admin / project_leader）。'
          : '权限点目录加载失败。',
      );
    }
  };

  const fetchRoles = async () => {
    const res = await getRoleList({ includePermissions: true });
    setRoles(extractRoleList(res));
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      await Promise.all([fetchRoles().catch(() => setRoles([])), fetchPermissions()]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const permissionGroups = useMemo(() => groupPermissions(permissions), [permissions]);
  const visibleGroups = useMemo(
    () => groupPermissions(filterPermissions(permissions, permissionKeyword)),
    [permissions, permissionKeyword],
  );
  const visibleIds = useMemo(
    () => visibleGroups.flatMap((group) => group.items.map((item) => item.id)),
    [visibleGroups],
  );
  const permissionLabelMap = useMemo(() => {
    const map = new Map<string, PermissionVO>();
    permissions.forEach((permission) => map.set(permission.id, permission));
    return map;
  }, [permissions]);

  const openCreate = () => {
    setModalMode('create');
    setCurrentRole(null);
    setSelectedIds([]);
    setPermissionKeyword('');
    setModalError(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const openEdit = async (record: RoleVO) => {
    setModalMode('edit');
    setCurrentRole(record);
    setPermissionKeyword('');
    setModalError(null);
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      description: record.description,
    });
    // 列表接口已附带权限点则直接回显；否则退回单角色查询（老后端无该端点时保持空选）
    if (Array.isArray(record.permissions)) {
      setSelectedIds(record.permissions.map((permission) => permission.id));
    } else {
      setSelectedIds([]);
      setRolePermissionLoading(true);
      try {
        const res: any = await getRolePermissions(record.id);
        const assigned = extractPermissionList(res);
        setSelectedIds(assigned.map((permission) => permission.id));
      } catch {
        // 回显失败不阻塞编辑：用户可重新勾选后保存
      } finally {
        setRolePermissionLoading(false);
      }
    }
    setIsModalVisible(true);
  };

  const togglePermission = (id: string, checked: boolean) => {
    setSelectedIds((prev) => (checked ? Array.from(new Set([...prev, id])) : prev.filter((item) => item !== id)));
  };

  const toggleVisibleGroup = (groupIds: string[], checked: boolean) => {
    setSelectedIds((prev) => {
      if (checked) {
        return Array.from(new Set([...prev, ...groupIds]));
      }
      return prev.filter((id) => !groupIds.includes(id));
    });
  };

  const handleDelete = (record: RoleVO) => {
    if (isSystemRole(record)) {
      message.warning('系统内置角色不可删除');
      return;
    }
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除角色 [${record.name}] 吗？该角色下的用户关联与权限关联会一并移除。`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteRole(record.id);
          message.success('删除成功');
          fetchData();
        } catch {
          // 404 / 400 等由响应拦截器提示（如角色不存在、内置角色不可删）
        }
      },
    });
  };

  const handleModalOk = async () => {
    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    // 权限点目录为空时省略 permissionIds，避免把角色已有授权覆盖成空集合
    const permissionIds = permissions.length > 0 ? selectedIds : undefined;
    setSaving(true);
    setModalError(null);
    try {
      if (modalMode === 'create') {
        const payload: CreateRoleRequest = {
          name: values.name,
          code: values.code,
          description: values.description,
          permissionIds,
        };
        await createRole(payload);
        message.success('角色创建成功');
      } else if (currentRole) {
        const payload: UpdateRoleRequest = {
          name: values.name,
          description: values.description,
          permissionIds,
        };
        await updateRole(currentRole.id, payload);
        message.success(
          permissionIds === undefined
            ? '角色信息已保存（未提交权限点）'
            : `权限已保存：${permissionIds.length} 个权限点`,
        );
      }
      setIsModalVisible(false);
      fetchData();
    } catch (error: any) {
      const status = getApiErrorStatus(error);
      const reason = getApiErrorMessage(error, '保存失败');
      setModalError(
        status === 404
          ? `${reason}（404：角色或权限点不存在，请关闭后刷新列表重试）`
          : `${reason}${status ? `（HTTP ${status}）` : ''}`,
      );
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { title: '角色名称', dataIndex: 'name', key: 'name' },
    {
      title: '角色编码',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => <Text code>{code}</Text>,
    },
    {
      title: '类型',
      key: 'isSystem',
      render: (_: unknown, record: RoleVO) =>
        isSystemRole(record) ? <Tag color="gold">系统内置</Tag> : <Tag color="green">自定义</Tag>,
    },
    {
      title: '权限点',
      key: 'permissionCount',
      render: (_: unknown, record: RoleVO) =>
        Array.isArray(record.permissions) ? (
          <Tag color={record.permissions.length > 0 ? 'blue' : 'default'}>
            {record.permissions.length} 个
          </Tag>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (text?: string) => text || <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: RoleVO) => (
        <Space size="middle">
          <a onClick={() => openEdit(record)}>配置权限</a>
          {isSystemRole(record) ? (
            <Tooltip title="系统内置角色不可删除">
              <Text type="secondary" style={{ cursor: 'not-allowed' }}>删除</Text>
            </Tooltip>
          ) : (
            <a style={{ color: 'red' }} onClick={() => handleDelete(record)}>删除</a>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="角色与权限管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建角色</Button>
        </Space>
      }
    >
      {permissionError && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`权限点目录：${permissionError}`}
          description="RBAC 权限点由后端启动初始化写入（约 90 个）。为空时角色仍可创建/编辑，但权限勾选区无内容，保存不会提交 permissionIds。"
        />
      )}

      <Table
        columns={columns}
        dataSource={roles}
        rowKey="id"
        loading={loading}
        pagination={false}
      />

      <Modal
        title={modalMode === 'create' ? '创建角色' : `配置角色权限 · ${currentRole?.name ?? ''}`}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={() => setIsModalVisible(false)}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
        destroyOnClose
        width={880}
      >
        <Form form={form} layout="vertical">
          {modalError && (
            <Alert
              type="error"
              showIcon
              closable
              style={{ marginBottom: 12 }}
              message="保存失败"
              description={modalError}
              onClose={() => setModalError(null)}
            />
          )}
          <Space size="large" style={{ display: 'flex', alignItems: 'flex-start' }}>
            <Form.Item
              name="name"
              label="角色名称"
              rules={[{ required: true, message: '请输入角色名称' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="如：安全运营" />
            </Form.Item>
            <Form.Item
              name="code"
              label="角色编码"
              rules={[{ required: true, message: '请输入角色编码' }]}
              style={{ flex: 1 }}
              extra={currentRole && isSystemRole(currentRole) ? '系统内置角色编码只读' : '创建后由后端按编码鉴权'}
            >
              <Input
                placeholder="如：security_ops"
                disabled={modalMode === 'edit'}
              />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="角色描述">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>

        <Divider titlePlacement="left" style={{ margin: '8px 0 12px' }}>
          权限点分配
        </Divider>

        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            <span>
              已选 <b>{selectedIds.length}</b> 个 / 共 <b>{permissions.length}</b> 个权限点
              {permissions.length > 0 && `（${permissionGroups.length} 个模块）`}
              {permissionIdsHint(permissions.length)}
            </span>
          }
        />

        <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索权限码 / 名称 / API 路径，如 asset:read"
            style={{ width: 360 }}
            value={permissionKeyword}
            onChange={(event) => setPermissionKeyword(event.target.value)}
          />
          <Space>
            <Button
              size="small"
              disabled={visibleIds.length === 0}
              onClick={() => toggleVisibleGroup(visibleIds, true)}
            >
              全选{permissionKeyword ? '（筛选结果）' : '全部'}
            </Button>
            <Button
              size="small"
              disabled={visibleIds.length === 0}
              onClick={() => toggleVisibleGroup(visibleIds, false)}
            >
              取消选择
            </Button>
          </Space>
        </Space>

        <Spin spinning={rolePermissionLoading}>
          <div
            style={{
              maxHeight: 360,
              overflowY: 'auto',
              border: '1px solid #f0f0f0',
              borderRadius: 6,
              padding: 12,
              background: '#fafafa',
            }}
          >
            {permissions.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无权限点数据（后端 RBAC 初始化未部署时为空态）"
              />
            ) : visibleGroups.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的权限点" />
            ) : (
              visibleGroups.map((group) => {
                const groupIds = group.items.map((item) => item.id);
                const groupSelected = groupIds.filter((id) => selectedIds.includes(id)).length;
                return (
                  <div key={group.module} style={{ marginBottom: 14 }}>
                    <Space style={{ marginBottom: 6 }}>
                      <Checkbox
                        checked={groupSelected === groupIds.length && groupIds.length > 0}
                        indeterminate={groupSelected > 0 && groupSelected < groupIds.length}
                        onChange={(event) => toggleVisibleGroup(groupIds, event.target.checked)}
                      >
                        <Text strong>{getPermissionModuleLabel(group.module)}</Text>
                        <Text type="secondary">（{groupSelected}/{groupIds.length}）</Text>
                      </Checkbox>
                    </Space>
                    <div style={{ paddingLeft: 24, display: 'flex', flexWrap: 'wrap', gap: '4px 16px' }}>
                      {group.items.map((permission) => (
                        <Checkbox
                          key={permission.id}
                          checked={selectedIds.includes(permission.id)}
                          onChange={(event) => togglePermission(permission.id, event.target.checked)}
                        >
                          <Tooltip title={permission.apiPath ? `${permission.httpMethod ?? ''} ${permission.apiPath}` : undefined}>
                            <span>
                              {permission.name}
                              <Text type="secondary" style={{ marginLeft: 4, fontSize: 12 }}>
                                {permission.code}
                              </Text>
                            </span>
                          </Tooltip>
                        </Checkbox>
                      ))}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </Spin>

        {selectedIds.length > 0 && (
          <div style={{ marginTop: 12 }}>
            <Text type="secondary" style={{ marginRight: 8 }}>已选权限码：</Text>
            <Space size={[4, 4]} wrap>
              {selectedIds.slice(0, 12).map((id) => {
                const permission = permissionLabelMap.get(id);
                return (
                  <Tag key={id} color="blue">{permission?.code ?? id}</Tag>
                );
              })}
              {selectedIds.length > 12 && <Tag>+{selectedIds.length - 12}</Tag>}
            </Space>
          </div>
        )}
      </Modal>
    </Card>
  );
};

/** 权限点为空时在提示里说明「保存不会提交 permissionIds」 */
const permissionIdsHint = (total: number): string =>
  total === 0 ? '；保存时不提交 permissionIds，不会清空角色已有授权' : '';

export default Roles;
