import React, { useEffect, useMemo, useState } from 'react';
import { Button, Modal, Spin, Tag, Tooltip, message } from 'antd';
import { EyeOutlined, ReloadOutlined, UndoOutlined } from '@ant-design/icons';
import { LOGIN_THEME_GROUP_KEYS, getLoginTheme, loginThemesByGroup } from '@/components/login/themes';
import { DEFAULT_LOGIN_THEME, type LoginThemeGroup, type LoginThemeId } from '@/components/login/types';
import {
  getMyPreferences,
  updateMyFollowDefault,
  updateMyPreferences,
  describePreferenceFailure,
  type UserPreferences,
} from '@/api/userPreferences';
import { getPublicLoginThemeConfig } from '@/api/auth';
import {
  clearLocalLoginThemeMark,
  readCachedDefaultLoginTheme,
  readLocalLoginThemeMark,
  writeLocalLoginThemeMark,
} from '@/utils/loginTheme';
import { useT } from '@/i18n';
import LoginThemeThumbnail from './LoginThemeThumbnail';

/**
 * 偏好设置弹窗：登录页主题（t97 建立 / t110 按 v2 语义重写）。
 *
 * ## v2 语义（用户更正后的模型）
 *
 * 登录页链条：`?theme=`（仅预览）> **本机标记** > **管理员缺省** > pencil。
 * 本机标记（`localStorage('login.theme')`）只在三种时机变化：
 * ①用户选主题（写）②「从账号同步到本机」（写）③「跟随系统默认」（清除）。
 * 登录成功与 `/auth/me` **不写**（跨设备不自动生效）；`users.login_theme` 只是
 * 「账号偏好」，用于预选与同步源。
 *
 * ## 本弹窗的三块操作
 *
 * - 卡片（`theme-card-*`）：选主题 → `PUT /users/me/preferences` + **写本机标记**；
 * - `pref-sync-from-account`：把账号偏好写成本机标记（跨设备手动同步的唯一入口）；
 * - `pref-follow-default`：清除本机标记（回到管理员缺省）。后端暂无「跟随默认」语义，
 *   同时把当前管理员缺省值写成账号偏好（保证「账号偏好」与「登录页实际显示」不打架），
 *   局限见下方 `LIMITATION_FOLLOW_DEFAULT` 注释与 t110 报告。
 *
 * 当前生效主题展示在 `pref-current-theme`，等于「本机标记 ?? 管理员缺省」。
 * 文案全部走 i18n；缩略图/文案都来自 `LOGIN_THEMES` 注册表，**没有硬编码主题列表**。
 */
export interface LoginThemePreferenceModalProps {
  onClose: () => void;
}

const GROUP_ORDER: LoginThemeGroup[] = ['character', 'abstract'];

/**
 * 后端三态（t112 起）：`users.login_theme`
 *
 * | 值 | 含义 | 前端表现 |
 * |---|---|---|
 * | 具体 code | 用户指定主题 | 对应卡片高亮 |
 * | `NULL` | **跟随系统默认** | 不高亮任何卡片，高亮「跟随系统默认」；登录页按管理员缺省渲染 |
 *
 * 「跟随系统默认」现在走**真正的第三态**：`PUT /users/me/preferences {followDefault: true}` →
 * 后端把 `users.login_theme` 置 NULL。**不再**像 t110 那样把当前管理员缺省写成账号偏好，
 * 因此管理员之后改缺省时，这些用户不会滞留旧值（t113 的核心修复点）。
 */

const LoginThemePreferenceModal: React.FC<LoginThemePreferenceModalProps> = ({ onClose }) => {
  const { t } = useT();
  /** 本机标记：null = 本浏览器没选过（登录页回落到管理员缺省） */
  const [localMark, setLocalMark] = useState<LoginThemeId | null>(() => readLocalLoginThemeMark());
  /** 账号偏好（三态）：`loginTheme=null` ⇔ 跟随系统默认。只用于预选/同步，不参与登录页解析 */
  const [account, setAccount] = useState<UserPreferences | null>(null);
  /** 管理员缺省：先取会话缓存，再后台刷新 */
  const [adminDefault, setAdminDefault] = useState<LoginThemeId>(
    () => readCachedDefaultLoginTheme()?.theme ?? DEFAULT_LOGIN_THEME,
  );
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<LoginThemeId | null>(null);
  /** 正在执行的整块操作（同步/跟随默认），用于按钮 loading 与防重复点击 */
  const [action, setAction] = useState<'sync' | 'followDefault' | null>(null);
  /** 服务端 400 拒绝过的主题：本次会话内标记为不可用，避免反复点同一个坑 */
  const [unavailable, setUnavailable] = useState<LoginThemeId[]>([]);
  const [previewTheme, setPreviewTheme] = useState<LoginThemeId | null>(null);

  useEffect(() => {
    let cancelled = false;
    getMyPreferences()
      .then((prefs) => {
        if (cancelled) return;
        // 只记录账号偏好，**不写本机标记**（打开弹窗不等于用户做出了选择）
        setAccount(prefs);
      })
      .catch((error) => {
        if (cancelled) return;
        const failure = describePreferenceFailure(error);
        if (failure.kind !== 'unauthorized') {
          message.warning(t('preferences.loginTheme.loadFailed', { theme: localMark ?? adminDefault }));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // 只在挂载时拉一次账号偏好：依赖里只放 t（语言变化时重新取文案，不影响请求次数语义）
  }, [t]);

  useEffect(() => {
    let cancelled = false;
    getPublicLoginThemeConfig()
      .then((config) => {
        if (!cancelled) setAdminDefault(config.defaultTheme);
      })
      .catch(() => {
        // 静默：管理员缺省取不到时用会话缓存 / pencil，不影响本弹窗其它操作
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const groups = useMemo(
    () =>
      GROUP_ORDER.map((group) => ({
        group,
        title: t(LOGIN_THEME_GROUP_KEYS[group]),
        entries: loginThemesByGroup(group),
      })).filter((item) => item.entries.length > 0),
    [t],
  );

  /** 登录页当前会显示的主题（= 本机标记 ?? 管理员缺省） */
  const effectiveTheme: LoginThemeId = localMark ?? adminDefault;
  const effectiveLabel = t(getLoginTheme(effectiveTheme).labelKey);
  const adminDefaultLabel = t(getLoginTheme(adminDefault).labelKey);

  /**
   * 账号是否处于「跟随系统默认」。
   *
   * 账号偏好还没取到时（`account === null`）按本机标记推断，避免首帧闪一下错误高亮。
   */
  const accountFollowsDefault = account ? account.followDefault : localMark === null;
  /** 账号当前的具体主题（跟随默认时为 null） */
  const accountTheme: LoginThemeId | null = account && !account.followDefault ? account.loginTheme : null;

  /**
   * 卡片是否高亮：**按账号偏好**预选（弹窗编辑的是账号偏好）。
   * 账号处于跟随默认时不高亮任何卡片（需求 4）——本机标记的状态另有 `pref-mark-state` 展示。
   */
  const isCardActive = (id: LoginThemeId): boolean => (account ? accountTheme === id : localMark === id);

  /** 选主题：先 PUT，成功后才写本机标记（避免"页面 A / 账号 B"分叉） */
  const choose = async (id: LoginThemeId) => {
    if (saving || action || id === accountTheme) return;
    setSaving(id);
    try {
      const saved = await updateMyPreferences(id);
      const written = writeLocalLoginThemeMark(saved.loginTheme);
      setLocalMark(written);
      setAccount(saved);
      setUnavailable((list) => list.filter((item) => item !== id));
      message.success(t('preferences.loginTheme.saved', { name: t(getLoginTheme(id).labelKey) }));
    } catch (error) {
      const failure = describePreferenceFailure(error);
      if (failure.kind === 'badRequest') {
        setUnavailable((list) => (list.includes(id) ? list : [...list, id]));
        message.warning(failure.text);
      } else if (failure.kind !== 'unauthorized') {
        message.error(failure.text);
      }
    } finally {
      setSaving(null);
    }
  };

  /**
   * 从账号同步到本机：把账号偏好写成本机标记（跨设备手动同步的唯一入口）。
   *
   * 账号处于「跟随系统默认」时**没有可同步的具体主题** → 按钮 disabled，点击时也只提示。
   * 注意「值恰好等于管理员缺省也要显式写入」：这里不做任何相等判断，直接写 `writeLocalLoginThemeMark`。
   */
  const syncFromAccount = async () => {
    if (saving || action) return;
    if (accountFollowsDefault) {
      message.info(t('preferences.syncDisabledFollowDefault'));
      return;
    }
    setAction('sync');
    try {
      let target = accountTheme;
      if (!target) {
        const latest = await getMyPreferences();
        setAccount(latest);
        target = latest.followDefault ? null : latest.loginTheme;
      }
      if (!target) {
        message.info(t('preferences.syncDisabledFollowDefault'));
        return;
      }
      const written = writeLocalLoginThemeMark(target);
      setLocalMark(written);
      message.success(t('preferences.loginTheme.synced', { name: t(getLoginTheme(target).labelKey) }));
    } catch (error) {
      const failure = describePreferenceFailure(error);
      if (failure.kind !== 'unauthorized') {
        message.error(failure.kind === 'network' ? failure.text : t('preferences.loginTheme.needAccountTheme'));
      }
    } finally {
      setAction(null);
    }
  };

  /**
   * 跟随系统默认（t113 第三态）：`PUT { followDefault: true }` → 后端置 NULL；**再**清本机标记。
   *
   * 顺序说明：先调后端、成功后才清本机标记 —— 后端失败时本机状态保持原样，
   * 不会出现「账号还是具体主题、本机却已跟随默认」的半截状态。
   * 成功后：`pref-current-theme` 的 `data-theme` = 当前管理员缺省、`data-source=adminDefault`。
   */
  const followDefault = async () => {
    if (saving || action) return;
    if (accountFollowsDefault && localMark === null) {
      message.info(t('preferences.followDefaultActive', { name: adminDefaultLabel }));
      return;
    }
    setAction('followDefault');
    try {
      const saved = await updateMyFollowDefault();
      setAccount(saved);
      clearLocalLoginThemeMark();
      setLocalMark(null);
      message.success(t('preferences.loginTheme.followedDefault', { name: adminDefaultLabel }));
    } catch (error) {
      const failure = describePreferenceFailure(error);
      if (failure.kind !== 'unauthorized') message.error(failure.text);
    } finally {
      setAction(null);
    }
  };

  const previewEntry = previewTheme ? getLoginTheme(previewTheme) : null;

  return (
    <>
      <Modal
        open
        title={t('preferences.title')}
        onCancel={onClose}
        width={760}
        maskClosable={false}
        footer={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <span style={{ fontSize: 12, color: '#6b7280', textAlign: 'left' }}>{t('preferences.loginTheme.orderHint')}</span>
            <span style={{ display: 'inline-flex', gap: 8 }}>
              <Button icon={<EyeOutlined />} onClick={() => setPreviewTheme(effectiveTheme)} data-testid="pref-preview">
                {t('preferences.loginTheme.preview')}
              </Button>
              <Button type="primary" onClick={onClose} data-testid="pref-close">
                {t('preferences.close')}
              </Button>
            </span>
          </div>
        }
      >
        <div className="pref-theme" data-testid="login-theme-preference">
          <div className="pref-theme__head">
            <div>
              <div className="pref-theme__title">{t('preferences.loginTheme.title')}</div>
              <div className="pref-theme__desc">{t('preferences.loginTheme.desc')}</div>
            </div>
            <Tag color="blue" data-testid="pref-current-theme" data-theme={effectiveTheme} data-source={localMark ? 'local' : 'adminDefault'}>
              {t('preferences.loginTheme.current', { name: effectiveLabel })}
            </Tag>
          </div>

          {/* 本机标记状态 + 账号偏好三态 + 两个整块操作 */}
          <div className="pref-theme__mark" data-testid="pref-mark-state" data-has-mark={localMark ? 'true' : 'false'}>
            <div className="pref-theme__mark-text">
              <div data-testid="pref-account-state" data-follow-default={accountFollowsDefault ? 'true' : 'false'}>
                {accountFollowsDefault
                  ? t('preferences.loginTheme.accountFollowDefault', { name: adminDefaultLabel })
                  : t('preferences.loginTheme.accountTheme', {
                      name: accountTheme ? t(getLoginTheme(accountTheme).labelKey) : '-',
                    })}
              </div>
              <div>
                {localMark
                  ? t('preferences.loginTheme.markLocal', { name: t(getLoginTheme(localMark).labelKey) })
                  : t('preferences.loginTheme.followingDefault', { name: adminDefaultLabel })}
              </div>
            </div>
            <div className="pref-theme__mark-actions">
              <Tooltip
                title={
                  accountFollowsDefault
                    ? t('preferences.syncDisabledFollowDefault')
                    : t('preferences.loginTheme.syncHint', {
                        name: accountTheme ? t(getLoginTheme(accountTheme).labelKey) : '-',
                      })
                }
              >
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  loading={action === 'sync'}
                  disabled={Boolean(saving) || action === 'followDefault' || accountFollowsDefault}
                  onClick={syncFromAccount}
                  data-testid="pref-sync-from-account"
                  data-disabled-reason={accountFollowsDefault ? 'followDefault' : undefined}
                >
                  {t('preferences.loginTheme.syncFromAccount')}
                </Button>
              </Tooltip>
              <Tooltip title={t('preferences.loginTheme.followDefaultHint', { name: adminDefaultLabel })}>
                <Button
                  size="small"
                  icon={<UndoOutlined />}
                  loading={action === 'followDefault'}
                  disabled={Boolean(saving) || action === 'sync' || (accountFollowsDefault && localMark === null)}
                  onClick={followDefault}
                  data-testid="pref-follow-default"
                  data-active={accountFollowsDefault && localMark === null ? 'true' : 'false'}
                >
                  {t('preferences.loginTheme.followDefault')}
                </Button>
              </Tooltip>
            </div>
          </div>

          {loading ? (
            <div className="pref-theme__loading">
              <Spin size="small" /> <span>{t('preferences.loginTheme.loading')}</span>
            </div>
          ) : null}

          {groups.map(({ group, title, entries }) => (
            <section key={group} className="pref-theme__group" data-testid={`pref-group-${group}`}>
              <div className="pref-theme__group-title">{title}</div>
              <div className="pref-theme__grid">
                {entries.map((entry) => {
                  // 高亮 = 账号偏好（三态）：跟随默认时任何卡片都不高亮（需求 4）
                  const active = isCardActive(entry.id);
                  const blocked = unavailable.includes(entry.id);
                  const busy = saving === entry.id;
                  return (
                    <Tooltip
                      key={entry.id}
                      title={blocked ? t('preferences.loginTheme.unavailableTag') : undefined}
                      open={blocked ? undefined : false}
                    >
                      <button
                        type="button"
                        className={['pref-theme__card', active ? 'is-active' : '', blocked ? 'is-blocked' : '']
                          .filter(Boolean)
                          .join(' ')}
                        aria-pressed={active}
                        aria-label={t(entry.labelKey)}
                        data-testid={`theme-card-${entry.id}`}
                        data-active={active ? 'true' : 'false'}
                        disabled={busy}
                        onClick={() => choose(entry.id)}
                      >
                        <LoginThemeThumbnail entry={entry} />
                        <div className="pref-theme__card-body">
                          <div className="pref-theme__card-head">
                            <span className="pref-theme__card-name">{t(entry.labelKey)}</span>
                            {active ? (
                              <Tag color="green" className="pref-theme__tag">
                                {t('preferences.loginTheme.selectedTag')}
                              </Tag>
                            ) : null}
                            {blocked ? (
                              <Tag color="orange" className="pref-theme__tag">
                                {t('preferences.loginTheme.unavailableTag')}
                              </Tag>
                            ) : null}
                          </div>
                          {entry.subtitleKey ? <div className="pref-theme__card-sub">{t(entry.subtitleKey)}</div> : null}
                        </div>
                      </button>
                    </Tooltip>
                  );
                })}
              </div>
            </section>
          ))}
        </div>

        <style>{`
          .pref-theme__head {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 12px;
          }
          .pref-theme__title {
            font-size: 15px;
            font-weight: 600;
            color: #1f2328;
          }
          .pref-theme__desc {
            font-size: 12px;
            color: #6b7280;
            margin-top: 4px;
            max-width: 520px;
            line-height: 1.6;
          }
          .pref-theme__mark {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
            background: #fafafa;
            border: 1px solid #f0f0f0;
            border-radius: 10px;
            padding: 8px 10px;
            margin-bottom: 8px;
          }
          .pref-theme__mark-text {
            font-size: 12px;
            color: #4b5563;
          }
          .pref-theme__mark-actions {
            display: inline-flex;
            gap: 8px;
          }
          .pref-theme__loading {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 12px;
            color: #6b7280;
            margin-bottom: 8px;
          }
          .pref-theme__group {
            margin-top: 8px;
          }
          .pref-theme__group-title {
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.4px;
            color: #8c8c8c;
            text-transform: uppercase;
            margin: 8px 0;
          }
          .pref-theme__grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
            gap: 10px;
          }
          .pref-theme__card {
            appearance: none;
            text-align: left;
            background: #fff;
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            padding: 8px;
            cursor: pointer;
            transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
            display: flex;
            flex-direction: column;
            gap: 6px;
            font: inherit;
            color: inherit;
          }
          .pref-theme__card:hover {
            border-color: #ff9a6c;
            box-shadow: 0 6px 18px rgba(31, 35, 40, 0.08);
          }
          .pref-theme__card:focus-visible {
            outline: 2px solid #ff6b35;
            outline-offset: 2px;
          }
          .pref-theme__card.is-active {
            border-color: #52c41a;
            box-shadow: 0 0 0 1px #52c41a inset;
          }
          .pref-theme__card.is-blocked {
            opacity: 0.72;
          }
          .pref-theme__card-head {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-wrap: wrap;
          }
          .pref-theme__card-name {
            font-size: 13px;
            font-weight: 600;
            color: #1f2328;
          }
          .pref-theme__tag {
            margin-inline-end: 0;
            font-size: 10px;
            line-height: 16px;
            padding: 0 6px;
          }
          .pref-theme__card-sub {
            font-size: 11px;
            color: #6b7280;
            line-height: 1.5;
          }
        `}</style>
      </Modal>

      {/* 预览：iframe 加载真实登录页（只读，不会提交任何请求） */}
      <Modal
        open={Boolean(previewEntry)}
        title={previewEntry ? t('preferences.loginTheme.previewTitle', { name: t(previewEntry.labelKey) }) : ''}
        onCancel={() => setPreviewTheme(null)}
        width={980}
        maskClosable={false}
        footer={
          <Button onClick={() => setPreviewTheme(null)} data-testid="pref-preview-close">
            {t('preferences.close')}
          </Button>
        }
      >
        {previewEntry ? (
          <div>
            <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 8 }}>
              {t('preferences.loginTheme.previewHint', { id: previewEntry.id })}
            </div>
            <iframe
              title={t('preferences.loginTheme.previewTitle', { name: t(previewEntry.labelKey) })}
              src={`/login?theme=${encodeURIComponent(previewEntry.id)}`}
              data-testid="pref-preview-frame"
              style={{ width: '100%', height: 560, border: '1px solid #e5e7eb', borderRadius: 10, background: '#fff' }}
            />
          </div>
        ) : null}
      </Modal>
    </>
  );
};

export default LoginThemePreferenceModal;
