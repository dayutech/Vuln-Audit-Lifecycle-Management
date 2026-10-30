import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
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
  Tree,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, TeamOutlined, UserAddOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import {
  addTeamMember,
  createTeam,
  deleteTeam,
  getAllTeams,
  getTeamMembers,
  removeTeamMember,
  updateTeam,
} from '@/api/team';
import { getMyAccessProfile, type MyAccessProfile } from '@/api/permission';
import type { TeamVO } from '@/types/team';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import { useUserStore } from '@/store/userStore';
import { describeTeamWriteFailure, isFormValidationError, resolveTeamWriteAccess } from './teamAccess';

const { TextArea } = Input;

interface TeamTreeNode {
  key: string;
  title: React.ReactNode;
  children: TeamTreeNode[];
}

interface TeamMemberRow {
  userId: string;
  name: string;
  username: string;
  isLeader: boolean;
}

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

const userName = (user?: UserDirectoryEntry | null) =>
  user ? (user.fullName || user.username) : '';

const Teams: React.FC = () => {
  const storeUser = useUserStore((state) => state.user);
  const [teams, setTeams] = useState<TeamVO[]>([]);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [memberIds, setMemberIds] = useState<string[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [profile, setProfile] = useState<MyAccessProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);

  const [modalVisible, setModalVisible] = useState(false);
  const [editingTeam, setEditingTeam] = useState<TeamVO | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [memberForm] = Form.useForm();
  const [memberSubmitting, setMemberSubmitting] = useState(false);

  const userMap = useMemo(() => {
    const map = new Map<string, UserDirectoryEntry>();
    users.forEach((user) => map.set(user.id, user));
    return map;
  }, [users]);

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

  const fetchTeams = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllTeams();
      const payload = getPayload(res);
      const list: TeamVO[] = Array.isArray(payload) ? payload : [];
      setTeams(list);
      setSelectedId((prev) => {
        if (prev && list.some((team) => team.id === prev)) {
          return prev;
        }
        return list[0]?.id ?? null;
      });
    } catch (error) {
      setTeams([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadProfile = async () => {
      setProfileLoading(true);
      try {
        // 复用 t1 的 /users/me/permissions（角色 + 权限点 + userId），不新增接口
        const next = await getMyAccessProfile();
        if (!cancelled) setProfile(next);
      } finally {
        if (!cancelled) setProfileLoading(false);
      }
    };
    loadProfile();
    return () => {
      cancelled = true;
    };
  }, []);

  /** 写入口可见性：角色（admin/project_leader/team_leader）+ 团队 leaderId 归属，口径对齐后端 t40 */
  const access = useMemo(
    () =>
      resolveTeamWriteAccess(profile, teams, {
        userId: storeUser?.id,
        roles: [storeUser?.role].filter(Boolean) as string[],
      }),
    [profile, teams, storeUser?.id, storeUser?.role],
  );

  useEffect(() => {
    fetchTeams();
  }, [fetchTeams]);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setUsers(list);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  const fetchMembers = useCallback(async (teamId: string | null) => {
    if (!teamId) {
      setMemberIds([]);
      return;
    }
    setMembersLoading(true);
    try {
      const res = await getTeamMembers(teamId);
      const payload = getPayload(res);
      setMemberIds(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setMemberIds([]);
    } finally {
      setMembersLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMembers(selectedId);
  }, [selectedId, fetchMembers]);

  // 树默认展开所有「有子团队」的节点；数据变化（新增子团队）后自动展开新分支
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  useEffect(() => {
    const parentIds = new Set<string>();
    teams.forEach((team) => {
      const parentId = team.parentId ?? team.parent_id;
      if (parentId) {
        parentIds.add(parentId);
      }
    });
    setExpandedKeys(Array.from(parentIds));
  }, [teams]);

  const selectedTeam = useMemo(() => teams.find((team) => team.id === selectedId) ?? null, [teams, selectedId]);

  const treeData = useMemo<TeamTreeNode[]>(() => {
    const ids = new Set(teams.map((team) => team.id));
    const childrenMap = new Map<string, TeamVO[]>();
    const roots: TeamVO[] = [];

    teams.forEach((team) => {
      const parentId = team.parentId ?? team.parent_id ?? null;
      if (!parentId || !ids.has(parentId)) {
        roots.push(team);
        return;
      }
      const bucket = childrenMap.get(parentId) ?? [];
      bucket.push(team);
      childrenMap.set(parentId, bucket);
    });

    const build = (team: TeamVO): TeamTreeNode => ({
      key: team.id,
      title: (
        <Space size={4}>
          <span>{team.name}</span>
          {(team.parentId ?? team.parent_id) === null || (team.parentId ?? team.parent_id) === undefined ? (
            <Tag color="blue">根</Tag>
          ) : null}
        </Space>
      ),
      children: (childrenMap.get(team.id) ?? []).map(build),
    });

    return roots.map(build);
  }, [teams]);

  const openCreate = (parentId?: string) => {
    setEditingTeam(null);
    form.resetFields();
    if (parentId) {
      form.setFieldsValue({ parent_id: parentId });
    }
    setModalVisible(true);
  };

  const openEdit = (team: TeamVO) => {
    setEditingTeam(team);
    form.resetFields();
    form.setFieldsValue({
      name: team.name,
      description: team.description,
      parent_id: team.parentId ?? team.parent_id ?? undefined,
      leader_id: team.leaderId ?? team.leader_id ?? undefined,
    });
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingTeam) {
        await updateTeam(editingTeam.id, {
          name: values.name,
          description: values.description,
          parent_id: values.parent_id ?? null,
          leader_id: values.leader_id,
        });
        message.success('团队更新成功');
      } else {
        await createTeam({
          name: values.name,
          description: values.description,
          parent_id: values.parent_id,
          leader_id: values.leader_id,
        });
        message.success('团队创建成功');
      }
      setModalVisible(false);
      await fetchTeams();
    } catch (error) {
      if (!isFormValidationError(error)) {
        message.error(describeTeamWriteFailure(error, editingTeam ? '团队更新失败' : '团队创建失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = (team: TeamVO) => {
    Modal.confirm({
      title: '确认删除团队',
      content: `确定删除「${team.name}」吗？子团队不会被级联删除，但会失去父团队归属。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteTeam(team.id);
          message.success('团队已删除');
          await fetchTeams();
        } catch (error) {
          message.error(describeTeamWriteFailure(error, '团队删除失败'));
        }
      },
    });
  };

  const handleAddMember = async () => {
    if (!selectedTeam) {
      message.warning('请先选择团队');
      return;
    }
    try {
      const values = await memberForm.validateFields();
      setMemberSubmitting(true);
      await addTeamMember(selectedTeam.id, { user_id: values.user_id, role: values.role });
      message.success('成员已添加');
      memberForm.resetFields();
      await fetchMembers(selectedTeam.id);
    } catch (error) {
      if (!isFormValidationError(error)) {
        message.error(describeTeamWriteFailure(error, '添加成员失败'));
      }
    } finally {
      setMemberSubmitting(false);
    }
  };

  const handleRemoveMember = async (userId: string) => {
    if (!selectedTeam) {
      return;
    }
    try {
      await removeTeamMember(selectedTeam.id, userId);
      message.success('成员已移除');
      await fetchMembers(selectedTeam.id);
    } catch (error) {
      message.error(describeTeamWriteFailure(error, '移除成员失败'));
    }
  };

  const memberRows = memberIds.map((userId) => {
    const user = userMap.get(userId);
    const leaderId = selectedTeam?.leaderId ?? selectedTeam?.leader_id ?? null;
    return {
      userId,
      name: user ? userName(user) : `${userId.slice(0, 8)}…`,
      username: user?.username ?? '-',
      isLeader: leaderId === userId,
    };
  });

  const memberColumns: TableColumnsType<TeamMemberRow> = [
    { title: '成员', dataIndex: 'name', key: 'name' },
    { title: '账号', dataIndex: 'username', key: 'username' },
    {
      title: '角色',
      key: 'isLeader',
      width: 120,
      render: (_: unknown, record: TeamMemberRow) =>
        record.isLeader ? <Tag color="gold">负责人</Tag> : <Tag>成员</Tag>,
    },
  ];

  // 只有能管理该团队成员时才渲染「移除」操作列（工程师/非本团队团队长完全看不到写入口）
  if (selectedTeam && access.canManageMembers(selectedTeam)) {
    memberColumns.push({
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, record: TeamMemberRow) => (
        <Popconfirm
          title="确认移除该成员？"
          onConfirm={() => handleRemoveMember(record.userId)}
          okText="移除"
          cancelText="取消"
        >
          <Typography.Link type="danger">移除</Typography.Link>
        </Popconfirm>
      ),
    });
  }

  const parentOptions = teams
    .filter((team) => !editingTeam || team.id !== editingTeam.id)
    .map((team) => ({ value: team.id, label: team.name }));

  const canManageSelected = Boolean(selectedTeam) && access.canManageMembers(selectedTeam);
  const isReadOnlyViewer = !access.isGlobalManager && !access.isAnyTeamLeader;

  return (
    <Card
      title="团队架构"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchTeams} loading={loading}>
            刷新
          </Button>
          {/* 新建团队：仅 admin / project_leader（POST /teams 的 @PreAuthorize 口径） */}
          {access.canCreateTeam ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>
              创建团队
            </Button>
          ) : null}
        </Space>
      }
    >
      <Spin spinning={profileLoading}>
        {isReadOnlyViewer ? (
          <Alert
            style={{ marginBottom: 16 }}
            type="info"
            showIcon
            message="只读视角"
            description={
              access.roles.includes('team_leader')
                ? '你是团队长，但当前没有领导任何团队：可以查看团队层级与成员，编辑/成员管理仅对你领导的团队开放。'
                : '当前账号没有团队管理权限：可以查看团队层级与成员，但不能新建、编辑、删除团队或管理成员。'
            }
          />
        ) : !access.isGlobalManager ? (
          <Alert
            style={{ marginBottom: 16 }}
            type="info"
            showIcon
            message="团队长视角"
            description="你可以编辑自己领导的团队并管理其成员；新建与删除团队仅管理员/项目负责人可操作。"
          />
        ) : null}
      </Spin>
      <Row gutter={24}>
        <Col xs={24} lg={7}>
          <Card size="small" title={<Space><TeamOutlined />团队层级</Space>} bodyStyle={{ minHeight: 420 }}>
            <Spin spinning={loading}>
              {treeData.length === 0 ? (
                <Empty description="暂无团队" />
              ) : (
                <Tree
                  blockNode
                  expandedKeys={expandedKeys}
                  onExpand={(keys) => setExpandedKeys(keys as React.Key[])}
                  selectedKeys={selectedId ? [selectedId] : []}
                  treeData={treeData}
                  onSelect={(keys) => {
                    // 再次点击已选中的节点时 antd 会返回空数组；保留当前选中，避免右侧详情被清空
                    if (keys.length > 0) {
                      setSelectedId(keys[0] as string);
                    }
                  }}
                />
              )}
            </Spin>
          </Card>
        </Col>
        <Col xs={24} lg={17}>
          {selectedTeam ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card
                size="small"
                title={selectedTeam.name}
                extra={
                  <Space wrap>
                    {/* 新增子团队 = POST /teams，仅 admin / project_leader */}
                    {access.canCreateTeam ? (
                      <Button size="small" onClick={() => openCreate(selectedTeam.id)}>
                        新增子团队
                      </Button>
                    ) : null}
                    {/* 编辑 = PUT /teams/{id}：admin/PL 任意团队，团队长仅自己领导的团队 */}
                    {access.canEditTeam(selectedTeam) ? (
                      <Button size="small" onClick={() => openEdit(selectedTeam)}>
                        编辑
                      </Button>
                    ) : null}
                    {/* 删除 = DELETE /teams/{id}：后端 and 语义，仅 admin / project_leader */}
                    {access.canDeleteTeam(selectedTeam) ? (
                      <Button size="small" danger onClick={() => handleDelete(selectedTeam)}>
                        删除
                      </Button>
                    ) : null}
                  </Space>
                }
              >
                <Descriptions
                  size="small"
                  column={{ xs: 1, sm: 2 }}
                  items={[
                    { key: 'name', label: '团队名称', children: selectedTeam.name },
                    {
                      key: 'parent',
                      label: '上级团队',
                      children:
                        teams.find((team) => team.id === (selectedTeam.parentId ?? selectedTeam.parent_id))?.name ?? '（根团队）',
                    },
                    {
                      key: 'leader',
                      label: '负责人',
                      children: userLabel(selectedTeam.leaderId ?? selectedTeam.leader_id),
                    },
                    { key: 'desc', label: '描述', children: selectedTeam.description || '-' },
                    {
                      key: 'created',
                      label: '创建时间',
                      children: selectedTeam.createdAt || selectedTeam.created_at || '-',
                    },
                    { key: 'members', label: '直属成员数', children: memberIds.length },
                  ]}
                />
              </Card>
              <Card
                size="small"
                title="团队成员"
                extra={
                  canManageSelected ? null : (
                    <Typography.Text type="secondary">仅可查看成员（无管理权限）</Typography.Text>
                  )
                }
              >
                {canManageSelected ? (
                  <Form form={memberForm} layout="inline" style={{ marginBottom: 16 }}>
                    <Form.Item name="user_id" rules={[{ required: true, message: '请选择用户' }]} style={{ minWidth: 240 }}>
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="选择要加入的用户"
                        options={users
                          .filter((user) => !memberIds.includes(user.id))
                          .map((user) => ({ value: user.id, label: `${userName(user)}（${user.username}）` }))}
                      />
                    </Form.Item>
                    <Form.Item name="role" initialValue="member">
                      <Select
                        style={{ width: 120 }}
                        options={[
                          { value: 'member', label: '成员' },
                          { value: 'leader', label: '负责人' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item>
                      <Button type="primary" icon={<UserAddOutlined />} loading={memberSubmitting} onClick={handleAddMember}>
                        添加成员
                      </Button>
                    </Form.Item>
                  </Form>
                ) : null}
                <Table
                  size="small"
                  rowKey="userId"
                  loading={membersLoading}
                  dataSource={memberRows}
                  pagination={false}
                  locale={{ emptyText: '该团队暂无直属成员' }}
                  columns={memberColumns}
                />
              </Card>
            </Space>
          ) : (
            <Card size="small">
              <Empty description="请选择左侧团队查看详情" />
            </Card>
          )}
        </Col>
      </Row>

      <Modal
        title={editingTeam ? '编辑团队' : '创建团队'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="团队名称" rules={[{ required: true, message: '请输入团队名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parent_id" label="上级团队">
            <Select allowClear showSearch optionFilterProp="label" placeholder="不选则为根团队" options={parentOptions} />
          </Form.Item>
          <Form.Item name="leader_id" label="负责人">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="请选择负责人"
              options={users.map((user) => ({ value: user.id, label: `${userName(user)}（${user.username}）` }))}
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

export default Teams;
