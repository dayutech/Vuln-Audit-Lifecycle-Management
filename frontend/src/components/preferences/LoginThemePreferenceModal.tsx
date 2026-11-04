import React, { useEffect, useMemo, useState } from 'react';
import { Button, Modal, Spin, Tag, Tooltip, message } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { LOGIN_THEME_GROUP_KEYS, getLoginTheme, loginThemesByGroup } from '@/components/login/themes';
import type { LoginThemeGroup, LoginThemeId } from '@/components/login/types';
import { getMyPreferences, updateMyPreferences, describePreferenceFailure } from '@/api/userPreferences';
import { readLoginThemeCache, syncLoginThemeFromServer, writeLoginThemeCache } from '@/utils/loginTheme';
import { useT } from '@/i18n';
import LoginThemeThumbnail from './LoginThemeThumbnail';

/**
 * 偏好设置弹窗：登录页主题（t97）。
 *
 * 行为约定：
 * - **打开即对齐服务端**：`GET /users/me/preferences` 成功后按服务端值同步本机缓存
 *   （跨设备换过主题时本机缓存会过期）；读取失败则退回本机缓存，不阻塞操作。
 * - **选中即保存**（没有"保存"按钮）：`PUT /users/me/preferences` 成功后
 *   **立即写 localStorage('login.theme')**，下次进登录页生效；不整页刷新。
 * - **只能改自己**：接口固定 `/me`，前端不传 userId（传了后端也忽略）。
 * - **失败分级**：400=服务端未启用该主题（t99 的四套抽象主题要等 t100 扩白名单，
 *   过渡期必须给明确解释并保持原主题）；403=无权（模拟视角）；5xx/网络=可读提示；
 *   401 由 request 拦截器统一提示并回登录页，这里不重复弹。
 * - **只给白名单**：卡片来自 `LOGIN_THEMES` 注册表，没有自由输入，UI 上无法触发非法值。
 * - **预览**：iframe 加载真实登录页 `?theme=<id>`（同一 no-auth 页面），只读查看配色与动效。
 */
export interface LoginThemePreferenceModalProps {
  onClose: () => void;
}

const GROUP_ORDER: LoginThemeGroup[] = ['character', 'abstract'];

const LoginThemePreferenceModal: React.FC<LoginThemePreferenceModalProps> = ({ onClose }) => {
  const { t } = useT();
  /** 当前选中/生效的主题（初值取本机缓存，随后被服务端值覆盖） */
  const [current, setCurrent] = useState<LoginThemeId>(() => readLoginThemeCache());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<LoginThemeId | null>(null);
  /** 服务端 400 拒绝过的主题：本次会话内标记为不可用，避免反复点同一个坑 */
  const [unavailable, setUnavailable] = useState<LoginThemeId[]>([]);
  const [previewTheme, setPreviewTheme] = useState<LoginThemeId | null>(null);

  useEffect(() => {
    let cancelled = false;
    getMyPreferences()
      .then((prefs) => {
        if (cancelled) return;
        // 服务端是权威：同步本机缓存并回显（脏值/缺失时 sync 会保留缓存，不刷回 pencil）
        const synced = syncLoginThemeFromServer(prefs.loginTheme);
        setCurrent(synced.theme);
      })
      .catch((error) => {
        if (cancelled) return;
        const failure = describePreferenceFailure(error);
        // 401 由拦截器提示并跳登录；其它情况提示"按本机缓存显示"
        if (failure.kind !== 'unauthorized') {
          message.warning(t('preferences.loginTheme.loadFailed', { theme: readLoginThemeCache() }));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [t]);

  const groups = useMemo(
    () =>
      GROUP_ORDER.map((group) => ({
        group,
        title: t(LOGIN_THEME_GROUP_KEYS[group]),
        entries: loginThemesByGroup(group),
      })).filter((item) => item.entries.length > 0),
    [t],
  );

  const currentLabel = useMemo(() => t(getLoginTheme(current).labelKey), [current, t]);

  const choose = async (id: LoginThemeId) => {
    if (saving || id === current) return;
    setSaving(id);
    try {
      const saved = await updateMyPreferences(id);
      // 只在服务端确认成功后才写缓存：本地不能出现"页面显示 A、账号偏好 B"的分叉
      const synced = writeLoginThemeCache(saved.loginTheme);
      setCurrent(synced);
      setUnavailable((list) => list.filter((item) => item !== id));
      message.success(t('preferences.loginTheme.saved', { name: t(getLoginTheme(synced).labelKey) }));
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

  const previewEntry = previewTheme ? getLoginTheme(previewTheme) : null;

  return (
    <>
      <Modal
        open
        title={t('preferences.title')}
        onCancel={onClose}
        width={720}
        maskClosable={false}
        footer={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <span style={{ fontSize: 12, color: '#6b7280', textAlign: 'left' }}>
              {t('preferences.loginTheme.orderHint')}
            </span>
            <span style={{ display: 'inline-flex', gap: 8 }}>
              <Button icon={<EyeOutlined />} onClick={() => setPreviewTheme(current)} data-testid="pref-preview">
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
            <Tag color="blue" data-testid="pref-current-theme">
              {t('preferences.loginTheme.current', { name: currentLabel })}
            </Tag>
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
                  const active = entry.id === current;
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
                        className={[
                          'pref-theme__card',
                          active ? 'is-active' : '',
                          blocked ? 'is-blocked' : '',
                        ]
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
                          {entry.subtitleKey ? (
                            <div className="pref-theme__card-sub">{t(entry.subtitleKey)}</div>
                          ) : null}
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
            max-width: 500px;
            line-height: 1.6;
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
