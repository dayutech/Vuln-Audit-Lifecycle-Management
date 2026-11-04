import type { ComponentType, ReactNode } from 'react';
import type { LoginCharacterProps, LoginThemeGroup, LoginThemeId } from './types';
import { LOGIN_THEME_GROUPS, LOGIN_THEME_LABEL_KEYS, LOGIN_THEME_SUBTITLE_KEYS } from './types';
import type { MessageKey } from '@/i18n/messages/zh-CN';
import PencilCharacter from './themes/pencil';
import GeometricCharacter from './themes/geometric';
import OwlCharacter from './themes/owl';
import PixelEyesCharacter from './themes/pixelEyes';
import AuroraTheme from './themes/aurora';
import MonoGridTheme from './themes/monoGrid';
import OrbitTheme from './themes/orbit';
import BigTypeTheme from './themes/bigType';

/**
 * 主题注册表条目。
 *
 * t97 的偏好弹窗只用**元数据**渲染缩略图（`palette` / `preview`），
 * 不要把 8 个动画实例塞进弹窗 —— 缩略图必须是静态的。
 */
export interface LoginThemeEntry {
  id: LoginThemeId;
  /** 主题名（i18n，供 t97 的选择器直接使用） */
  labelKey: MessageKey;
  /** 一句话说明（t97 卡片副标题） */
  subtitleKey?: MessageKey;
  /** 分组：角色 / 抽象极简（t97 分组标题用 `LOGIN_THEME_GROUP_KEYS`） */
  group: LoginThemeGroup;
  /** 2–3 个代表性颜色（t97 缩略底色/色点，顺序即主色优先） */
  palette?: string[];
  /** 自定义静态缩略图（可选；缺省时 t97 用 palette 画色块） */
  preview?: ReactNode;
  /**
   * 主题自带「超大产品名」：登录页品牌区不再重复渲染标题，
   * 避免与背景大字撞名（目前只有 big-type）。
   */
  carriesWordmark?: boolean;
  Component: ComponentType<LoginCharacterProps>;
}

/** 分组 id → 分组标题（t97 的分组渲染直接复用） */
export const LOGIN_THEME_GROUP_KEYS: Record<LoginThemeGroup, MessageKey> = {
  character: 'login.theme.group.character',
  abstract: 'login.theme.group.abstract',
};

/** 主题注册表（顺序即选择器展示顺序：先角色、后抽象） */
export const LOGIN_THEMES: LoginThemeEntry[] = [
  // ---------- t95：卡通角色（占左侧角色位） ----------
  {
    id: 'pencil',
    labelKey: LOGIN_THEME_LABEL_KEYS.pencil,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.pencil,
    group: LOGIN_THEME_GROUPS.pencil,
    palette: ['#f7f1e6', '#1f2328', '#ff6b35'],
    Component: PencilCharacter,
  },
  {
    id: 'geometric',
    labelKey: LOGIN_THEME_LABEL_KEYS.geometric,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.geometric,
    group: LOGIN_THEME_GROUPS.geometric,
    palette: ['#dcecff', '#2b59c3', '#ffffff'],
    Component: GeometricCharacter,
  },
  {
    id: 'owl',
    labelKey: LOGIN_THEME_LABEL_KEYS.owl,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.owl,
    group: LOGIN_THEME_GROUPS.owl,
    palette: ['#f6ecd9', '#4a3728', '#e8b84b'],
    Component: OwlCharacter,
  },
  {
    id: 'pixel-eyes',
    labelKey: LOGIN_THEME_LABEL_KEYS['pixel-eyes'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['pixel-eyes'],
    group: LOGIN_THEME_GROUPS['pixel-eyes'],
    palette: ['#05150d', '#0f2f1e', '#39ff88'],
    Component: PixelEyesCharacter,
  },
  // ---------- t99：抽象极简（铺满整页做背景层，不做角色） ----------
  {
    id: 'aurora',
    labelKey: LOGIN_THEME_LABEL_KEYS.aurora,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.aurora,
    group: LOGIN_THEME_GROUPS.aurora,
    palette: ['#0a0c11', '#7c9cff', '#eef1fb'],
    Component: AuroraTheme,
  },
  {
    id: 'mono-grid',
    labelKey: LOGIN_THEME_LABEL_KEYS['mono-grid'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['mono-grid'],
    group: LOGIN_THEME_GROUPS['mono-grid'],
    palette: ['#f4f5f7', '#141821', '#9aa1ad'],
    Component: MonoGridTheme,
  },
  {
    id: 'orbit',
    labelKey: LOGIN_THEME_LABEL_KEYS.orbit,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.orbit,
    group: LOGIN_THEME_GROUPS.orbit,
    palette: ['#0f1218', '#e8ecf5', '#9db4ff'],
    Component: OrbitTheme,
  },
  {
    id: 'big-type',
    labelKey: LOGIN_THEME_LABEL_KEYS['big-type'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['big-type'],
    group: LOGIN_THEME_GROUPS['big-type'],
    palette: ['#f4f2ee', '#111318', '#8b8a86'],
    carriesWordmark: true,
    Component: BigTypeTheme,
  },
];

/** 按分组取主题（t97 的分组列表，顺序保持注册表顺序） */
export const loginThemesByGroup = (group: LoginThemeGroup): LoginThemeEntry[] =>
  LOGIN_THEMES.filter((entry) => entry.group === group);

export const getLoginTheme = (id: LoginThemeId): LoginThemeEntry =>
  LOGIN_THEMES.find((entry) => entry.id === id) ?? LOGIN_THEMES[0];

export default LOGIN_THEMES;
