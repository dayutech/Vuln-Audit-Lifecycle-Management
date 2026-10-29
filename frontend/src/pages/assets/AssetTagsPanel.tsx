import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { addAssetTag, deleteAssetTag, getAssetTags } from '@/api/asset';
import type { AssetTagVO } from '@/types/asset';
import { getUserList } from '@/api/user';
import type { UserVO } from '@/types/user';

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

const userName = (user?: UserVO | null) =>
  user ? (user.full_name || (user as any).fullName || user.username) : '';

interface AssetTagsPanelProps {
  assetId: string;
}

/**
 * 资产详情页「标签」区块：GET/POST/DELETE /assets/{id}/tags。
 * 独立组件以便资产详情页保持小改动面。
 */
const AssetTagsPanel: React.FC<AssetTagsPanelProps> = ({ assetId }) => {
  const [tags, setTags] = useState<AssetTagVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [tagInput, setTagInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [users, setUsers] = useState<UserVO[]>([]);

  const userMap = useMemo(() => new Map(users.map((user) => [user.id, user])), [users]);

  const loadTags = useCallback(async () => {
    if (!assetId) {
      return;
    }
    setLoading(true);
    try {
      const res = await getAssetTags(assetId);
      const payload = getPayload(res);
      setTags(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setTags([]);
    } finally {
      setLoading(false);
    }
  }, [assetId]);

  useEffect(() => {
    loadTags();
  }, [loadTags]);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const res = await getUserList({ page: 1, page_size: 200 });
        const payload = getPayload(res);
        setUsers(payload?.content || payload?.items || []);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  const handleAdd = async () => {
    const value = tagInput.trim();
    if (!value) {
      message.warning('请输入标签名称');
      return;
    }
    setSubmitting(true);
    try {
      await addAssetTag(assetId, value);
      message.success('标签已添加');
      setTagInput('');
      await loadTags();
    } catch (error) {
      // 拦截器已提示（如「标签已存在」）
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (tag: AssetTagVO) => {
    try {
      await deleteAssetTag(assetId, tag.id);
      message.success('标签已移除');
      await loadTags();
    } catch (error) {
      // 拦截器已提示
    }
  };

  const createdByLabel = (id?: string | null) => {
    if (!id) {
      return '-';
    }
    const user = userMap.get(id);
    return user ? userName(user) : `${id.slice(0, 8)}…`;
  };

  return (
    <div style={{ padding: '24px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <h3 style={{ margin: 0 }}>资产标签</h3>
          <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>
            标签用于跨分组检索（例如：客户名称、项目批次、组件类型），一个资产可打多个标签。
          </p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={loadTags} loading={loading}>
          刷新
        </Button>
      </div>

      <Space.Compact style={{ maxWidth: 460, marginBottom: 16 }}>
        <Input
          placeholder="输入标签名称后回车添加"
          value={tagInput}
          maxLength={50}
          onChange={(event) => setTagInput(event.target.value)}
          onPressEnter={handleAdd}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={submitting} onClick={handleAdd}>
          添加标签
        </Button>
      </Space.Compact>

      {tags.length === 0 ? (
        <Empty description="该资产暂无标签" />
      ) : (
        <>
          <Space wrap size={[8, 8]} style={{ marginBottom: 16 }}>
            {tags.map((tag) => (
              <Tag
                key={tag.id}
                color="blue"
                closable
                onClose={(event) => {
                  event.preventDefault();
                  handleRemove(tag);
                }}
              >
                {tag.tag}
              </Tag>
            ))}
          </Space>
          <Table
            size="small"
            rowKey="id"
            loading={loading}
            pagination={false}
            dataSource={tags}
            columns={[
              { title: '标签', dataIndex: 'tag', key: 'tag', render: (value: string) => <Tag color="blue">{value}</Tag> },
              {
                title: '创建人',
                key: 'createdBy',
                width: 180,
                render: (_: unknown, record: AssetTagVO) => createdByLabel(record.createdBy ?? record.created_by),
              },
              {
                title: '创建时间',
                key: 'createdAt',
                width: 200,
                render: (_: unknown, record: AssetTagVO) => {
                  const value = record.createdAt ?? record.created_at;
                  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
                },
              },
              {
                title: '操作',
                key: 'action',
                width: 100,
                render: (_: unknown, record: AssetTagVO) => (
                  <Typography.Link type="danger" onClick={() => handleRemove(record)}>
                    移除
                  </Typography.Link>
                ),
              },
            ]}
          />
        </>
      )}
    </div>
  );
};

export default AssetTagsPanel;
