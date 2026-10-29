import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Card, Spin, Tabs, Typography } from 'antd';
import {
  AppstoreOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import ImportPanel from './ImportPanel';
import { getMyAccessProfile } from '@/api/permission';
import { canImportAssets, canImportUsers, isTeamLeaderScope } from '@/api/import';

type ImportTabKey = 'assets' | 'vulnerabilities' | 'users';

const VALID_TABS: ImportTabKey[] = ['assets', 'vulnerabilities', 'users'];

const isTabKey = (value: string | null): value is ImportTabKey =>
  !!value && (VALID_TABS as string[]).includes(value);

/**
 * 数据导入（/import）
 *
 * Tab 可见性按后端写侧授权预判（避免用户点了才 403）：
 * - 资产导入（POST /import/assets）：admin / project_leader / team_leader；
 *   工程师（vulnerability_engineer）**不渲染该 Tab**，深链 ?tab=assets 回落到第一个可用 Tab；
 * - 漏洞导入（POST /import/vulnerabilities）：不加角色门槛，后端逐行按目标资产 canWriteAsset 判定；
 * - 用户导入（POST /import/users）：仅 admin。
 *
 * 失败行区分「权限拒绝：」（写权限被拒，不会落库）与数据校验失败，
 * 结果区拆分计数并给出「不要整份重传」的提示。
 *
 * 深链：?tab=assets|vulnerabilities|users。
 */
const ImportPage: React.FC = () => {
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

  const allowAssets = canImportAssets(roles);
  const allowUsers = canImportUsers(roles);
  const teamLeaderScope = isTeamLeaderScope(roles);

  const items = useMemo(() => {
    const list: Array<{ key: ImportTabKey; label: React.ReactNode; children: React.ReactNode }> = [];

    if (allowAssets) {
      list.push({
        key: 'assets',
        label: (
          <span>
            <AppstoreOutlined /> 资产导入
          </span>
        ),
        children: <ImportPanel type="assets" teamScopeHint={teamLeaderScope} />,
      });
    }

    // 漏洞导入对所有登录角色开放，逐行按资产写权限判定
    list.push({
      key: 'vulnerabilities',
      label: (
        <span>
          <SafetyCertificateOutlined /> 漏洞导入
        </span>
      ),
      children: <ImportPanel type="vulnerabilities" />,
    });

    if (allowUsers) {
      list.push({
        key: 'users',
        label: (
          <span>
            <TeamOutlined /> 用户导入
          </span>
        ),
        children: <ImportPanel type="users" />,
      });
    }

    return list;
  }, [allowAssets, allowUsers, teamLeaderScope]);

  const requested = isTabKey(tabParam) ? tabParam : null;
  const allowedKeys = items.map((item) => item.key);
  // 深链到无权/不存在的 Tab 时回落到第一个可用 Tab
  const activeKey: ImportTabKey =
    requested && allowedKeys.includes(requested) ? requested : (allowedKeys[0] ?? 'vulnerabilities');
  const blockedByRole = requested !== null && !allowedKeys.includes(requested);

  return (
    <Card title="数据导入">
      {profileLoaded ? (
        <>
          {blockedByRole && (
            <Alert
              type="info"
              showIcon
              closable
              style={{ marginBottom: 12 }}
              message={blockedByRoleMessage(requested)}
              description={`已切换到「${tabLabel(activeKey)}」。`}
            />
          )}
          <Tabs
            activeKey={activeKey}
            items={items}
            onChange={(key) => {
              const next = new URLSearchParams(searchParams);
              if (key === items[0]?.key) {
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
            <Typography.Text type="secondary">正在获取权限画像…</Typography.Text>
          </div>
        </div>
      )}
    </Card>
  );
};

const tabLabel = (key: ImportTabKey): string => {
  switch (key) {
    case 'assets':
      return '资产导入';
    case 'users':
      return '用户导入';
    default:
      return '漏洞导入';
  }
};

/** 深链被权限拦截时的说明（资产导入的 403 是后端 @PreAuthorize 写死的） */
const blockedByRoleMessage = (requested: ImportTabKey | null): string => {
  switch (requested) {
    case 'assets':
      return '当前账号无权导入资产（后端仅 ADMIN / PROJECT_LEADER / TEAM_LEADER 可写）';
    case 'users':
      return '当前账号无权导入用户（后端仅 ADMIN 可写）';
    default:
      return '当前账号无权访问该导入类型';
  }
};

export default ImportPage;
