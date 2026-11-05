import type { ComponentType, ReactNode } from 'react';
import type { LoginCharacterProps, LoginFormSkin, LoginThemeGroup, LoginThemeId, LoginThemeTone } from './types';
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
 * t101 起每条还带 `formSkin`：登录框的专属皮肤（颜色/圆角/描边/输入形态/字重）。
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
  /** 登录框专属皮肤（t101）：登录页把它换成 `--skin-*` CSS 变量 */
  formSkin: LoginFormSkin;
  /** 舞台明暗（t108）：登录页左上角徽标据此选 `color` / `monochrome` 版 */
  tone: LoginThemeTone;
  /** 自定义静态缩略图（可选；缺省时 t97 用 palette 画色块） */
  preview?: ReactNode;
  /**
   * 主题自带「超大产品名」：登录页的品牌区不再重复渲染产品名，
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

/**
 * 八套登录框皮肤（t101）。
 *
 * 体系化规则：
 * - **角色向**（pencil / geometric / owl / pixel-eyes）：大圆角（或像素质感直角）、粗描边、
 *   圆润按钮、浅色填充输入框，配色取自各自角色；
 * - **抽象向**（aurora / mono-grid / orbit / big-type）：小圆角、1px 细描边、
 *   直角或微圆角按钮、输入框无填充（下划线或细描边），深色主题用半透明 + 背景模糊。
 *
 * 可读性：深色卡片一律配浅色文字，浅色卡片配深色文字；按钮色与按钮文字反相。
 */
export const LOGIN_FORM_SKINS: Record<LoginThemeId, LoginFormSkin> = {
  pencil: {
    cardBg: '#fffaf2',
    cardBorder: '2px solid #1f2328',
    cardRadius: '18px',
    cardPadding: 'clamp(20px, 2.2vw, 30px)',
    cardShadow: '5px 5px 0 rgba(31, 35, 40, 0.9)',
    ink: '#1f2328',
    inkSoft: '#6d5f4d',
    inputBorder: '#d8cbb4',
    inputBg: '#fffdf7',
    inputFocus: '#ff6b35',
    inputVariant: 'outline',
    labelWeight: 600,
    buttonRadius: '999px',
    buttonBg: '#ff6b35',
    // 橙底配深墨字（对比度 5.5:1）；白字只有 2.8:1，明亮底上不达标
    buttonColor: '#1f2328',
  },
  geometric: {
    cardBg: '#ffffff',
    cardBorder: '3px solid #2b59c3',
    cardRadius: '26px',
    cardPadding: 'clamp(20px, 2.2vw, 30px)',
    cardShadow: '0 22px 48px rgba(43, 89, 195, 0.18)',
    ink: '#12294f',
    inkSoft: '#52678a',
    inputBorder: '#b9d3f5',
    inputBg: '#f7fbff',
    inputFocus: '#2b59c3',
    inputVariant: 'outline',
    labelWeight: 700,
    buttonRadius: '14px',
    buttonBg: '#2b59c3',
    buttonColor: '#ffffff',
  },
  owl: {
    cardBg: '#fdf8ee',
    cardBorder: '2px solid #4a3728',
    cardRadius: '22px',
    cardPadding: 'clamp(20px, 2.2vw, 30px)',
    cardShadow: '0 18px 42px rgba(74, 55, 40, 0.16)',
    ink: '#3b2c20',
    inkSoft: '#7c6753',
    inputBorder: '#e3d3b6',
    inputBg: '#f6ecd9',
    inputFocus: '#4a3728',
    inputVariant: 'filled',
    labelWeight: 600,
    buttonRadius: '22px',
    buttonBg: '#e8b84b',
    buttonColor: '#3a2a1c',
  },
  'pixel-eyes': {
    cardBg: 'rgba(5, 21, 13, 0.88)',
    cardBorder: '2px solid #39ff88',
    cardRadius: '0px',
    cardPadding: 'clamp(20px, 2.2vw, 30px)',
    cardShadow: '6px 6px 0 rgba(57, 255, 136, 0.22)',
    ink: '#d8ffe6',
    inkSoft: 'rgba(216, 255, 230, 0.62)',
    inputBorder: 'rgba(57, 255, 136, 0.45)',
    inputBg: 'rgba(57, 255, 136, 0.07)',
    inputFocus: '#39ff88',
    inputVariant: 'outline',
    labelWeight: 700,
    buttonRadius: '0px',
    buttonBg: '#39ff88',
    buttonColor: '#05150d',
  },
  aurora: {
    cardBg: 'rgba(10, 12, 17, 0.62)',
    cardBackdrop: 'blur(14px)',
    cardBorder: '1px solid rgba(238, 241, 251, 0.18)',
    cardRadius: '6px',
    cardPadding: 'clamp(22px, 2.4vw, 32px)',
    ink: '#eef1fb',
    inkSoft: 'rgba(238, 241, 251, 0.58)',
    inputBorder: 'rgba(238, 241, 251, 0.28)',
    inputBg: 'transparent',
    inputFocus: '#7c9cff',
    inputVariant: 'underline',
    labelWeight: 500,
    buttonRadius: '2px',
    buttonBg: '#7c9cff',
    buttonColor: '#0a0c11',
  },
  'mono-grid': {
    cardBg: 'rgba(255, 255, 255, 0.92)',
    cardBackdrop: 'blur(6px)',
    cardBorder: '1px solid rgba(20, 24, 33, 0.14)',
    cardRadius: '2px',
    cardPadding: 'clamp(22px, 2.4vw, 32px)',
    ink: '#141821',
    inkSoft: 'rgba(20, 24, 33, 0.56)',
    inputBorder: 'rgba(20, 24, 33, 0.24)',
    inputBg: 'transparent',
    inputFocus: '#141821',
    inputVariant: 'outline',
    labelWeight: 500,
    buttonRadius: '2px',
    buttonBg: '#141821',
    buttonColor: '#ffffff',
  },
  orbit: {
    cardBg: 'rgba(15, 18, 24, 0.6)',
    cardBackdrop: 'blur(12px)',
    cardBorder: '1px solid rgba(232, 236, 245, 0.22)',
    cardRadius: '4px',
    cardPadding: 'clamp(22px, 2.4vw, 32px)',
    ink: '#e8ecf5',
    inkSoft: 'rgba(232, 236, 245, 0.6)',
    inputBorder: 'rgba(232, 236, 245, 0.3)',
    inputBg: 'transparent',
    inputFocus: '#9db4ff',
    inputVariant: 'underline',
    labelWeight: 500,
    buttonRadius: '4px',
    buttonBg: '#9db4ff',
    buttonColor: '#0f1218',
  },
  'big-type': {
    cardBg: '#ffffff',
    cardBorder: '1px solid rgba(17, 19, 24, 0.16)',
    cardRadius: '0px',
    cardPadding: 'clamp(22px, 2.4vw, 32px)',
    ink: '#111318',
    inkSoft: 'rgba(17, 19, 24, 0.55)',
    inputBorder: 'rgba(17, 19, 24, 0.28)',
    inputBg: 'transparent',
    inputFocus: '#111318',
    inputVariant: 'underline',
    labelWeight: 300,
    buttonRadius: '0px',
    buttonBg: '#111318',
    buttonColor: '#ffffff',
  },
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
    formSkin: LOGIN_FORM_SKINS.pencil,
    tone: 'light',
    Component: PencilCharacter,
  },
  {
    id: 'geometric',
    labelKey: LOGIN_THEME_LABEL_KEYS.geometric,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.geometric,
    group: LOGIN_THEME_GROUPS.geometric,
    palette: ['#dcecff', '#2b59c3', '#ffffff'],
    formSkin: LOGIN_FORM_SKINS.geometric,
    tone: 'light',
    Component: GeometricCharacter,
  },
  {
    id: 'owl',
    labelKey: LOGIN_THEME_LABEL_KEYS.owl,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.owl,
    group: LOGIN_THEME_GROUPS.owl,
    palette: ['#f6ecd9', '#4a3728', '#e8b84b'],
    formSkin: LOGIN_FORM_SKINS.owl,
    tone: 'light',
    Component: OwlCharacter,
  },
  {
    id: 'pixel-eyes',
    labelKey: LOGIN_THEME_LABEL_KEYS['pixel-eyes'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['pixel-eyes'],
    group: LOGIN_THEME_GROUPS['pixel-eyes'],
    palette: ['#05150d', '#0f2f1e', '#39ff88'],
    formSkin: LOGIN_FORM_SKINS['pixel-eyes'],
    tone: 'dark',
    Component: PixelEyesCharacter,
  },
  // ---------- t99：抽象极简（铺满整页做背景层，不做角色） ----------
  {
    id: 'aurora',
    labelKey: LOGIN_THEME_LABEL_KEYS.aurora,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.aurora,
    group: LOGIN_THEME_GROUPS.aurora,
    palette: ['#0a0c11', '#7c9cff', '#eef1fb'],
    formSkin: LOGIN_FORM_SKINS.aurora,
    tone: 'dark',
    Component: AuroraTheme,
  },
  {
    id: 'mono-grid',
    labelKey: LOGIN_THEME_LABEL_KEYS['mono-grid'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['mono-grid'],
    group: LOGIN_THEME_GROUPS['mono-grid'],
    palette: ['#f4f5f7', '#141821', '#9aa1ad'],
    formSkin: LOGIN_FORM_SKINS['mono-grid'],
    tone: 'light',
    Component: MonoGridTheme,
  },
  {
    id: 'orbit',
    labelKey: LOGIN_THEME_LABEL_KEYS.orbit,
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS.orbit,
    group: LOGIN_THEME_GROUPS.orbit,
    palette: ['#0f1218', '#e8ecf5', '#9db4ff'],
    formSkin: LOGIN_FORM_SKINS.orbit,
    tone: 'dark',
    Component: OrbitTheme,
  },
  {
    id: 'big-type',
    labelKey: LOGIN_THEME_LABEL_KEYS['big-type'],
    subtitleKey: LOGIN_THEME_SUBTITLE_KEYS['big-type'],
    group: LOGIN_THEME_GROUPS['big-type'],
    palette: ['#f4f2ee', '#111318', '#8b8a86'],
    formSkin: LOGIN_FORM_SKINS['big-type'],
    tone: 'light',
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
