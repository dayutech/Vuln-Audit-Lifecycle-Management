import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { deleteFormDefinition, listFormDefinitions, resetEnabledDefinitionsCache, type FormDefinitionVO } from '@/api/forms';
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/errorMessage';
import { useT } from '@/i18n';

/**
 * 表单模板列表（t182，仅 ADMIN）。
 *
 * 契约：`GET /admin/form-definitions?keyword=&enabled=&page=&size=`；
 * 删除被实例引用的模板 → 409（后端拒绝，前端提示原因，不做乐观移除）。
 */
const FormDefinitionsPage: React.FC = () => {
  const { t } = useT();
  const navigate = useNavigate();
  const [rows, setRows] = useState<FormDefinitionVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>();
  const [params, setParams] = useState<{ page: number; size: number; keyword: string; enabled?: boolean }>({
    page: 0,
    size: 10,
    keyword: '',
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const page = await listFormDefinitions(params);
      setRows(page.content);
      setTotal(page.totalElements);
    } catch (error) {
      if (getApiErrorStatus(error) === 403) {
        message.error(t('form.def.forbidden'));
        return;
      }
      message.error(getApiErrorMessage(error, t('form.def.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [params, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = (row: FormDefinitionVO) => {
    Modal.confirm({
      title: t('form.def.deleteTitle'),
      content: t('form.def.deleteConfirm', { name: row.name }),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteFormDefinition(row.id);
          message.success(t('form.def.deleted'));
          resetEnabledDefinitionsCache();
          await load();
        } catch (error) {
          if (getApiErrorStatus(error) === 409) {
            message.error(t('form.def.deleteFailed'));
            return;
          }
          message.error(getApiErrorMessage(error, t('form.def.deleteFailed')));
        }
      },
    });
  };

  const columns = [
    { title: t('form.def.colCode'), dataIndex: 'code', key: 'code', width: 180 },
    {
      title: t('form.def.colName'),
      dataIndex: 'name',
      key: 'name',
      render: (value: string, row: FormDefinitionVO) => (
        <a onClick={() => navigate(`/admin/form-definitions/${row.id}/edit`)} data-testid={`form-def-open-${row.id}`}>
          {value}
        </a>
      ),
    },
    { title: t('form.def.colFields'), dataIndex: 'fieldCount', key: 'fieldCount', width: 100 },
    {
      title: t('form.def.colEnabled'),
      key: 'enabled',
      width: 120,
      render: (_: unknown, row: FormDefinitionVO) => (
        <Tag color={row.enabled ? 'green' : 'default'}>{row.enabled ? t('form.def.enabled') : t('form.def.disabled')}</Tag>
      ),
    },
    {
      title: t('form.def.colActions'),
      key: 'actions',
      width: 200,
      fixed: 'right' as const,
      render: (_: unknown, row: FormDefinitionVO) => (
        <Space size="middle">
          <a onClick={() => navigate(`/admin/form-definitions/${row.id}/edit`)} data-testid={`form-def-edit-${row.id}`}>
            {t('form.def.edit')}
          </a>
          <a onClick={() => handleDelete(row)} data-testid={`form-def-delete-${row.id}`}>
            {t('common.delete')}
          </a>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title={t('form.def.title')}
        subtitle={t('form.def.subtitle')}
        fallbackTo="/dashboard"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void load()} />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/admin/form-definitions/new')}
              data-testid="form-def-create"
            >
              {t('form.def.create')}
            </Button>
          </Space>
        }
      />
      <Card data-testid="form-def-list">
        <Space wrap style={{ marginBottom: 16 }}>
          <Input
            style={{ width: 260 }}
            prefix={<SearchOutlined />}
            placeholder={t('form.def.filterKeyword')}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => setParams((prev) => ({ ...prev, keyword, page: 0 }))}
            allowClear
            data-testid="form-def-search"
          />
          <Select
            allowClear
            style={{ width: 160 }}
            placeholder={t('form.def.filterEnabled')}
            value={enabledFilter}
            onChange={(value) => {
              setEnabledFilter(value);
              setParams((prev) => ({ ...prev, enabled: value, page: 0 }));
            }}
            options={[
              { value: true, label: t('form.def.enabled') },
              { value: false, label: t('form.def.disabled') },
            ]}
            data-testid="form-def-filter-enabled"
          />
          <Button type="primary" onClick={() => setParams((prev) => ({ ...prev, keyword, page: 0 }))}>
            {t('common.search')}
          </Button>
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 900 }}
          pagination={{
            current: params.page + 1,
            pageSize: params.size,
            total,
            showSizeChanger: false,
            onChange: (page) => setParams((prev) => ({ ...prev, page: page - 1 })),
          }}
        />
      </Card>
    </>
  );
};

export default FormDefinitionsPage;

