import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Input, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { addAssetTag, deleteAssetTag, getAssetTags } from '@/api/asset';
import type { AssetTagVO } from '@/types/asset';
import { getUserDirectory, USER_DIRECTORY_DEFAULT_LIMIT } from '@/api/user';
import type { UserDirectoryEntry } from '@/api/user';
import { useT } from '@/i18n';

const getPayload = (res: unknown) => ((res as any)?.data ?? res);

const userName = (user?: UserDirectoryEntry | null) =>
  user ? (user.fullName || user.username) : '';

interface AssetTagsPanelProps {
  assetId: string;
  /**
   * t149：详情接口已内联 tags（后端 t145），首屏直接用，省掉一次 GET /assets/{id}/tags。
   * 后续新增/删除标签仍走单资产接口刷新（任务允许"按需刷新单/少量资产"）。
   */
  initialTagNames?: string[];
  /** 资产写权（负责人或管理层）；false 时隐藏新增/删除标签入口（t49 起 DELETE 标签需 canWriteAsset） */
  canWrite?: boolean;
}

/**
 * 资产详情页「标签」区块：GET/POST/DELETE /assets/{id}/tags。
 * 独立组件以便资产详情页保持小改动面。
 */
const AssetTagsPanel: React.FC<AssetTagsPanelProps> = ({ assetId, canWrite = false, initialTagNames }) => {
  const { t } = useT();
  const [tags, setTags] = useState<AssetTagVO[]>(
    (initialTagNames ?? []).map(
      (name, index) => ({ id: `inline-${index}`, assetId, tag: name }) as unknown as AssetTagVO,
    ),
  );
  const [loading, setLoading] = useState(false);
  const [tagInput, setTagInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [users, setUsers] = useState<UserDirectoryEntry[]>([]);

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

  // t149：有内联标签时不发首屏请求（详情页不必为标签再打一次单资产接口）
  const hasInlineTags = Boolean(initialTagNames && initialTagNames.length > 0);
  useEffect(() => {
    if (hasInlineTags) return;
    loadTags();
  }, [loadTags, hasInlineTags]);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        // 标签创建人展示走用户目录（工程师也可读），不再请求 GET /users
        const list = await getUserDirectory({ limit: USER_DIRECTORY_DEFAULT_LIMIT });
        setUsers(list);
      } catch (error) {
        setUsers([]);
      }
    };
    fetchUsers();
  }, []);

  const handleAdd = async () => {
    const value = tagInput.trim();
    if (!value) {
      message.warning(t('asset.tags.inputRequired'));
      return;
    }
    setSubmitting(true);
    try {
      await addAssetTag(assetId, value);
      message.success(t('asset.tags.addSuccess'));
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
      message.success(t('asset.tags.removeSuccess'));
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
          {/* t143：去掉与 tab 同名的「资产标签」重复标题，仅保留说明 */}
          <p style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c' }}>{t('asset.tags.desc')}</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={loadTags} loading={loading}>{t('common.refresh')}</Button>
      </div>

      {canWrite ? (
      <Space.Compact style={{ maxWidth: 460, marginBottom: 16 }}>
        <Input
          placeholder={t('asset.tags.inputPlaceholder')}
          value={tagInput}
          maxLength={50}
          onChange={(event) => setTagInput(event.target.value)}
          onPressEnter={handleAdd}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={submitting} onClick={handleAdd}>{t('asset.tags.add')}</Button>
      </Space.Compact>
      ) : (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={t('asset.tags.readonly')}
        />
      )}

      {tags.length === 0 ? (
        <Empty description={t('asset.tags.empty')} />
      ) : (
        <>
          <Space wrap size={[8, 8]} style={{ marginBottom: 16 }}>
            {tags.map((tag) => (
              <Tag
                key={tag.id}
                color="blue"
                closable={canWrite}
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
              { title: t('asset.tags.colTag'), dataIndex: 'tag', key: 'tag', render: (value: string) => <Tag color="blue">{value}</Tag> },
              {
                title: t('asset.tags.colCreator'),
                key: 'createdBy',
                width: 180,
                render: (_: unknown, record: AssetTagVO) => createdByLabel(record.createdBy ?? record.created_by),
              },
              {
                title: t('asset.tags.colCreatedAt'),
                key: 'createdAt',
                width: 200,
                render: (_: unknown, record: AssetTagVO) => {
                  const value = record.createdAt ?? record.created_at;
                  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
                },
              },
              {
                title: t('asset.tags.colAction'),
                key: 'action',
                width: 100,
                render: (_: unknown, record: AssetTagVO) => (
                  <Typography.Link type="danger" onClick={() => handleRemove(record)}>{t('asset.tags.remove')}</Typography.Link>
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
