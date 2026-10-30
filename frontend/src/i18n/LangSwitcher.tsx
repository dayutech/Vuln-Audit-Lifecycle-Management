import React from 'react';
import { Dropdown, Button, Space } from 'antd';
import { GlobalOutlined, DownOutlined } from '@ant-design/icons';
import { useT, type Lang } from '@/i18n';

/**
 * 语言切换（页头右上角）。
 *
 * 切换后立即生效（Context state 驱动 + ConfigProvider locale + dayjs locale 同步），
 * 无需刷新；偏好写入 localStorage('app.lang')，刷新后保持。
 */
const LangSwitcher: React.FC = () => {
  const { lang, setLang, t } = useT();

  const options: { key: Lang; label: string }[] = [
    { key: 'zh-CN', label: t('common.langZh') },
    { key: 'en-US', label: t('common.langEn') },
  ];

  return (
    <Dropdown
      menu={{
        selectable: true,
        selectedKeys: [lang],
        items: options.map((option) => ({
          key: option.key,
          label: option.label,
          onClick: () => setLang(option.key),
        })),
      }}
      placement="bottomRight"
      trigger={['click']}
    >
      <Button type="text" icon={<GlobalOutlined />} data-testid="lang-switcher">
        <Space size={4}>
          {lang === 'en-US' ? t('common.langEn') : t('common.langZh')}
          <DownOutlined style={{ fontSize: 10 }} />
        </Space>
      </Button>
    </Dropdown>
  );
};

export default LangSwitcher;
