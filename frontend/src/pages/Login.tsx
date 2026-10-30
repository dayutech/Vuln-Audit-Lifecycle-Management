import React, { useState } from 'react';
import { Card, Form, Input, Button, message, Space } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login } from '@/api/auth';
import { useUserStore } from '@/store/userStore';
import { useT } from '@/i18n';
import LangSwitcher from '@/i18n/LangSwitcher';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setToken, setUser } = useUserStore();
  const { t } = useT();

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const res = await login(values);
      // The backend returns { success: true, data: { token, ... } }
      if (res && res.data && res.data.token) {
        message.success(t('login.success'));
        setToken(res.data.token);
        // Transform the flat response to user object format expected by store
        setUser({
          id: res.data.userId,
          username: res.data.username,
          full_name: res.data.fullName,
          email: res.data.email,
          role: 'admin' // Or whatever default role until fetched
        } as any);
        navigate('/dashboard');
      }
    } catch (error) {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#f0f2f5' }}>
      <Card
        title={t('login.title')}
        extra={<LangSwitcher />}
        style={{ width: 420, textAlign: 'center' }}
      >
        <Form
          name="login"
          initialValues={{ remember: true }}
          onFinish={onFinish}
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: t('login.usernameRequired') }]}
          >
            <Input prefix={<UserOutlined />} placeholder={t('login.username')} />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: t('login.passwordRequired') }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder={t('login.password')} />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" style={{ width: '100%' }} loading={loading}>
              {t('login.submit')}
            </Button>
          </Form.Item>
        </Form>
        <Space size={4} style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>
          {/* 后端错误信息（如账号密码错误）仍为后端中文，前端不翻译 */}
        </Space>
      </Card>
    </div>
  );
};

export default Login;
