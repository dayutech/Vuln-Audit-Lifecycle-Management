import React, { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Typography } from 'antd';
import { SafetyCertificateOutlined, AppstoreOutlined } from '@ant-design/icons';
import { getAssetStats, getVulnerabilityStats } from '@/api/dashboard';
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';

const { Title } = Typography;

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042'];
const SEVERITY_COLORS = { critical: '#f5222d', high: '#fa8c16', medium: '#1890ff', low: '#52c41a' };
const SEVERITY_LABELS: Record<string, string> = { critical: '严重', high: '高危', medium: '中危', low: '低危' };

const Dashboard: React.FC = () => {
  const [assetStats, setAssetStats] = useState<any>(null);
  const [vulnStats, setVulnStats] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchStats = async () => {
      setLoading(true);
      try {
        const [assetRes, vulnRes] = await Promise.all([
          getAssetStats(),
          getVulnerabilityStats()
        ]);
        setAssetStats(assetRes.data || (assetRes as any));
        setVulnStats(vulnRes.data || (vulnRes as any));
      } catch (error) {
        console.error('Failed to fetch dashboard stats', error);
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  const renderAssetTypeChart = () => {
    if (!assetStats?.by_type) return null;
    const data = Object.keys(assetStats.by_type).map(key => ({
      name: key,
      value: assetStats.by_type[key]
    }));
    return (
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie data={data} cx="50%" cy="50%" outerRadius={100} fill="#8884d8" dataKey="value" label>
            {data.map((_, index) => <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />)}
          </Pie>
          <Tooltip />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    );
  };

  const renderVulnSeverityChart = () => {
    if (!vulnStats?.by_severity) return null;
    const data = Object.keys(vulnStats.by_severity).map(key => ({
      name: SEVERITY_LABELS[key] || key,
      value: vulnStats.by_severity[key],
      fill: (SEVERITY_COLORS as any)[key] || '#8884d8'
    }));
    return (
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="name" />
          <YAxis />
          <Tooltip />
          <Bar dataKey="value">
            {data.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={entry.fill} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    );
  };

  return (
    <div>
      <Title level={2}>仪表盘</Title>
      
      <Row gutter={[16, 16]}>
        <Col span={12}>
          <Card loading={loading}>
            <Statistic 
              title="资产总数" 
              value={assetStats?.total || 0} 
              prefix={<AppstoreOutlined />} 
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card loading={loading}>
            <Statistic 
              title="漏洞总数" 
              value={vulnStats?.total || 0} 
              prefix={<SafetyCertificateOutlined />} 
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="资产类型分布" loading={loading}>
            {renderAssetTypeChart()}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="漏洞严重程度分布" loading={loading}>
            {renderVulnSeverityChart()}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
