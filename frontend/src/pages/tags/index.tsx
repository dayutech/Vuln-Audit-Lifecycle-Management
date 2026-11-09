import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  ColorPicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  createTagDefinition,
  deleteTagDefinition,
  listTagDefinitions,
  updateTagDefinition,
  type TagDefinition,
} from '@/api/tag';
import { getMyAccessProfile } from '@/api/permission';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 管理员：标签定义管理（t143，后端 t141）。
 *
 * 口径（与后端一致）：
 * - 内置标签（`builtin`）**只能改颜色/排序**：改名/删除后端 403，这里直接把输入框与删除按钮禁掉，少一次打脸；
 * - 删除是**软删**且**名称唯一含软删行**（删掉的名字不能复用），被资产引用时后端 409（消息带引用数）；
 * - 每次写操作后端只写 1 条 `UPDATE_TAG_DEFINITION` 审计，前端不额外发请求。
 *
 * 权限：整页仅 ADMIN（菜单入口按画像隐藏；页面再判一次，非 ADMIN 显示 403）。
 */
const TagsAdminPage: React.FC = () => {
  const { t } = useT();
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [items, setItems] = useState<TagDefinition[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [keywordApplied, setKeywordApplied] = useState('');
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<TagDefinition | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ name?: string; color?: any; sortOrder?: number }>();

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile()
      .then((profile) => {
        if (!cancelled) setIsAdmin(profile.roles.includes('admin'));
      })
      .catch(() => {
        if (!cancelled) setIsAdmin(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(async () => {
    if (isAdmin !== true) return;
    setLoading(true);
    setError(null);
    try {
      const { items: rows, total: count } = await listTagDefinitions({
        keyword: keywordApplied || undefined,
        includeDeleted,
        page,
        page_size: pageSize,
      });
      setItems(rows);
      setTotal(count);
    } catch (err) {
      setItems([]);
      setTotal(0);
      const status = getApiErrorStatus(err);
      if (status === 403) {
        setIsAdmin(false);
        return;
      }
      setError(getApiErrorMessage(err, t('common.operationFailed')));
    } finally {
      setLoading(false);
    }
  }, [isAdmin, keywordApplied, includeDeleted, page, pageSize, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ sortOrder: (items[items.length - 1]?.sortOrder ?? 0) + 10 });
    setModalOpen(true);
  };

  const openEdit = (row: TagDefinition) => {
    setEditing(row);
    form.setFieldsValue({
      name: row.name,
      color: row.color ?? undefined,
      sortOrder: row.sortOrder,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const colorValue = values.color;
      const color =
        typeof colorValue === 'string'
          ? colorValue
          : colorValue?.toHexString
            ? colorValue.toHexString()
            : undefined;
      const payload = { name: values.name?.trim(), color: color ?? null, sortOrder: values.sortOrder ?? null };
      if (editing) {
        // 内置标签只允许改颜色/排序：name 不传，避免后端 403
        await updateTagDefinition(editing.id, editing.builtin ? { color: payload.color, sortOrder: payload.sortOrder } : payload);
        message.success(t('tag.updateSuccess'));
      } else {
        await createTagDefinition(payload);
        message.success(t('tag.createSuccess'));
      }
      setModalOpen(false);
      await load();
    } catch (err) {
      if ((err as any)?.errorFields) return; // 表单校验失败
      const status = getApiErrorStatus(err);
      if (status === 409) message.error(t('tag.duplicate', { message: getApiErrorMessage(err) }));
      else if (status === 403) message.error(t('tag.builtinLocked'));
      else message.error(getApiErrorMessage(err, t('common.operationFailed')));
    } finally {
      setSaving(false);
    }
  };

  const remove = (row: TagDefinition) => {
    Modal.confirm({
      title: t('tag.deleteTitle'),
      content: t('tag.deleteContent', { name: row.name }),
      okText: t('common.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteTagDefinition(row.id);
          message.success(t('tag.deleteSuccess'));
          await load();
        } catch (err) {
          const status = getApiErrorStatus(err);
          if (status === 409) message.error(t('tag.inUse', { message: getApiErrorMessage(err) }));
          else if (status === 403) message.error(t('tag.builtinLocked'));
          else message.error(getApiErrorMessage(err, t('common.operationFailed')));
        }
      },
    });
  };

  if (isAdmin === false) {
    return (
      <Card data-testid="tag-admin-forbidden">
        <Alert type="error" showIcon message={t('tag.forbidden')} />
      </Card>
    );
  }

  return (
    <Card
      title={<span data-testid="tag-admin-title">{t('tag.title')}</span>}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()} data-testid="tag-refresh">
            {t('common.refresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} data-testid="tag-create">
            {t('tag.new')}
          </Button>
        </Space>
      }
      data-testid="tag-admin-page"
    >
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message={t('tag.hint')} />
      {error ? <Alert type="error" showIcon style={{ marginBottom: 12 }} message={error} /> : null}

      <Space wrap style={{ marginBottom: 12 }}>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          style={{ width: 240 }}
          value={keyword}
          placeholder={t('tag.searchPlaceholder')}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => {
            setKeywordApplied(keyword.trim());
            setPage(1);
          }}
          data-testid="tag-search"
        />
        <Space size={6}>
          <Switch
            checked={includeDeleted}
            onChange={(checked) => {
              setIncludeDeleted(checked);
              setPage(1);
            }}
            data-testid="tag-include-deleted"
          />
          <span style={{ fontSize: 13 }}>{t('tag.includeDeleted')}</span>
        </Space>
      </Space>

      <Table<TagDefinition>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={items}
        locale={{ emptyText: t('tag.empty') }}
        data-testid="tag-table"
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
          },
        }}
        columns={[
          {
            title: t('tag.colName'),
            dataIndex: 'name',
            key: 'name',
            render: (value: string, row: TagDefinition) => (
              <Space size={6} data-testid={`tag-row-${row.id}`} data-builtin={String(row.builtin)} data-deleted={String(Boolean(row.deletedAt))}>
                <Tag color={row.color || 'blue'}>{value}</Tag>
                {row.builtin ? <Tag color="gold">{t('tag.builtin')}</Tag> : null}
                {row.deletedAt ? <Tag color="default">{t('softDelete.deletedTag')}</Tag> : null}
              </Space>
            ),
          },
          { title: t('tag.colColor'), dataIndex: 'color', key: 'color', width: 130, render: (v?: string) => v || '-' },
          { title: t('tag.colSort'), dataIndex: 'sortOrder', key: 'sortOrder', width: 90 },
          {
            title: t('tag.colAction'),
            key: 'action',
            width: 180,
            render: (_: unknown, row: TagDefinition) => (
              <Space size="small">
                <a onClick={() => openEdit(row)} data-testid={`tag-edit-${row.id}`}>
                  {t('common.edit')}
                </a>
                {!row.builtin && !row.deletedAt ? (
                  <a style={{ color: 'red' }} onClick={() => remove(row)} data-testid={`tag-delete-${row.id}`}>
                    {t('common.delete')}
                  </a>
                ) : null}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        open={modalOpen}
        title={editing ? t('tag.modalEdit') : t('tag.modalCreate')}
        onCancel={() => setModalOpen(false)}
        onOk={() => void submit()}
        confirmLoading={saving}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <div data-testid="tag-modal">
          <Form form={form} layout="vertical">
            <Form.Item
              name="name"
              label={t('tag.colName')}
              rules={[{ required: true, message: t('tag.nameRequired') }]}
              extra={editing?.builtin ? t('tag.builtinNameLocked') : undefined}
            >
              <Input maxLength={50} disabled={Boolean(editing?.builtin)} data-testid="tag-form-name" />
            </Form.Item>
            <Form.Item name="color" label={t('tag.colColor')}>
              <ColorPicker data-testid="tag-form-color" />
            </Form.Item>
            <Form.Item name="sortOrder" label={t('tag.colSort')}>
              <InputNumber style={{ width: '100%' }} data-testid="tag-form-sort" />
            </Form.Item>
          </Form>
        </div>
      </Modal>
    </Card>
  );
};

export default TagsAdminPage;
