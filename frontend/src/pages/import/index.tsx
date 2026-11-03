import React, { useEffect, useState } from 'react';
import { Alert, Card, Spin, Tabs, Typography } from 'antd';
import {
  AppstoreOutlined,
  BankOutlined,
  ProjectOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import ImportPanel from './ImportPanel';
import { getMyAccessProfile } from '@/api/permission';
import { canImportType, IMPORT_TYPE_META, isTeamLeaderScope } from '@/api/import';
import type { ImportType } from '@/api/import';
import { useT } from '@/i18n';

/** Tab 顺序（与需求一致）：资产 → 漏洞 → 用户 → 客户 → 项目 */
const TAB_ORDER: ImportType[] = ['assets', 'vulnerabilities', 'users', 'customers', 'projects'];

const TAB_ICONS: Record<ImportType, React.ReactNode> = {
  assets: <AppstoreOutlined />,
  vulnerabilities: <SafetyCertificateOutlined />,
  users: <TeamOutlined />,
  customers: <BankOutlined />,
  projects: <ProjectOutlined />,
};

const isTabKey = (value: string | null): value is ImportType =>
  !!value && (TAB_ORDER as string[]).includes(value);

/**
 * 数据导入（/import）
 *
 * Tab 可见性按后端写侧授权预判（避免用户点了才 403）：
 * - 资产导入（POST /import/assets）：admin / project_leader / team_leader；
 * - 漏洞导入（POST /import/vulnerabilities）：不加角色门槛，后端逐行按目标资产 canWriteAsset 判定；
 * - 用户导入（POST /import/users）：仅 admin；
 * - 客户导入（POST /import/customers）：admin / project_leader（t85，团队长 403）；
 * - 项目导入（POST /import/projects）：admin / project_leader。
 *
 * 无权/不存在的深链（?tab=xxx）会回落到第一个可用 Tab，并给出说明。
 */
const ImportPage: React.FC = () => {
  const { t } = useT();
  const [searchParams, setSearchParams] = useSearchParams();
  const [roles, setRoles] = useState<string[]>([]);
  /** 权限画像返回前不渲染 Tab，避免先渲染无权 Tab 再跳走的闪烁 */
  const [profileLoaded, setProfileLoaded] = useState(false);
  const tabParam = searchParams.get('tab');

  useEffect(() => {
    let cancelled = false;
    getMyAccessProfile()
      .then((profile) => {
        if (!cancelled) setRoles(profile.roles);
      })
      .finally(() => {
        if (!cancelled) setProfileLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const allowedTabs = TAB_ORDER.filter((type) => canImportType(roles, type));
  const teamLeaderScope = isTeamLeaderScope(roles);

  /** Tab 内容：数量最多 5 个，直接构造即可（无需 useMemo） */
  const items = allowedTabs.map((type) => ({
    key: type,
    label: (
      <span>
        {TAB_ICONS[type]} {t(IMPORT_TYPE_META[type].labelKey)}
      </span>
    ),
    children: <ImportPanel type={type} teamScopeHint={type === 'assets' && teamLeaderScope} />,
  }));

  const requested = isTabKey(tabParam) ? tabParam : null;
  // 深链到无权/不存在的 Tab 时回落到第一个可用 Tab
  const activeKey: ImportType =
    requested && allowedTabs.includes(requested) ? requested : (allowedTabs[0] ?? 'vulnerabilities');
  const blockedByRole = requested !== null && !allowedTabs.includes(requested);

  const blockedMessage = (type: ImportType): string => {
    switch (type) {
      case 'assets':
        return t('import.blocked.assets');
      case 'users':
        return t('import.blocked.users');
      case 'customers':
        return t('import.blocked.customers');
      case 'projects':
        return t('import.blocked.projects');
      default:
        return t('import.blocked.generic');
    }
  };

  return (
    <Card title={t('import.title')}>
      {profileLoaded ? (
        <>
          {blockedByRole && (
            <Alert
              type="info"
              showIcon
              closable
              style={{ marginBottom: 12 }}
              message={blockedMessage(requested as ImportType)}
              description={t('import.switchedTo', { label: t(IMPORT_TYPE_META[activeKey].labelKey) })}
            />
          )}
          <Tabs
            activeKey={activeKey}
            items={items}
            onChange={(key) => {
              const next = new URLSearchParams(searchParams);
              if (key === allowedTabs[0]) {
                next.delete('tab');
              } else {
                next.set('tab', key);
              }
              setSearchParams(next, { replace: true });
            }}
          />
        </>
      ) : (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin />
          <div style={{ marginTop: 12 }}>
            <Typography.Text type="secondary">{t('import.loadingProfile')}</Typography.Text>
          </div>
        </div>
      )}
    </Card>
  );
};

export default ImportPage;
