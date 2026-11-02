import { useEffect, useState } from 'react';
import { Card, Table, Space, Tag, Button, Modal, Input, message } from 'antd';
import { getMyAccessApprovalRequests, reviewAssetAccessRequest } from '@/api/asset';
import type { AssetAccessVO } from '@/types/asset';
import { useT } from '@/i18n';

/**
 * 审批中心：展示路由给当前用户的待审批访问申请。
 *
 * 后端 `/assets/my/access-approval-requests` 已按审批路由过滤：
 * admin / project_leader 可见全部待审，其他人只看路由给自己的。
 */
const Approvals: React.FC = () => {
  const { t } = useT();
  const [data, setData] = useState<AssetAccessVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [reviewingId, setReviewingId] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      const res: any = await getMyAccessApprovalRequests({ page: 1, page_size: 50 });
      const payload = res?.data ?? res;
      setData(Array.isArray(payload?.content) ? payload.content : []);
    } catch (error) {
      // 错误由 axios 拦截器统一提示
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleReview = (record: AssetAccessVO, approved: boolean) => {
    let reviewNote = '';
    Modal.confirm({
      title: approved ? '通过访问申请' : '驳回访问申请',
      content: (
        <Input.TextArea
          rows={3}
          placeholder="审批备注（可选）"
          onChange={(e) => { reviewNote = e.target.value; }}
        />
      ),
      okText: approved ? '通过' : '驳回',
      okButtonProps: approved ? {} : { danger: true },
      onOk: async () => {
        try {
          setReviewingId(record.id);
          await reviewAssetAccessRequest(record.id, approved, reviewNote || undefined);
          message.success(approved ? '已通过，申请人获得访问授权' : '已驳回');
          await fetchData();
        } catch (error: any) {
          message.error(error?.response?.data?.message || '审批失败');
        } finally {
          setReviewingId(null);
        }
      },
    });
  };

  const scopeText = (record: AssetAccessVO) => {
    const parts: string[] = [];
    if (record.canViewDocuments) parts.push('文档');
    if (record.canViewEnvironments) parts.push('环境');
    if (record.canViewFiles) parts.push('文件');
    return parts.length ? parts.join(' / ') : '仅基本信息';
  };

  const columns = [
    {
      title: t('approvals.colAssetId'),
      dataIndex: 'assetId',
      key: 'assetId',
      render: (text: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{text}</span>,
    },
    {
      title: t('approvals.colApplicantId'),
      dataIndex: 'userId',
      key: 'userId',
      render: (text: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{text}</span>,
    },
    {
      title: t('approvals.colScope'),
      key: 'scope',
      render: (_: unknown, record: AssetAccessVO) => <Tag color="blue">{scopeText(record)}</Tag>,
    },
    { title: t('approvals.colNote'), dataIndex: 'requestNote', key: 'requestNote', render: (text: string) => text || '-' },
    {
      title: t('approvals.colSubmittedAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => (text ? new Date(text).toLocaleString() : '-'),
    },
    {
      title: t('approvals.colAction'),
      key: 'action',
      render: (_: unknown, record: AssetAccessVO) => (
        <Space size="middle">
          <Button
            type="link"
            loading={reviewingId === record.id}
            onClick={() => handleReview(record, true)}
          >
            通过
          </Button>
          <Button type="link" danger onClick={() => handleReview(record, false)}>
            驳回
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card title={t('approvals.title')} extra={<Button onClick={fetchData}>{t('common.refresh')}</Button>}>
      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: t('approvals.empty') }}
      />
    </Card>
  );
};

export default Approvals;
