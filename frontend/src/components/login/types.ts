import type { MessageKey } from '@/i18n/messages/zh-CN';

/**
 * 八套登录主题 id（t95 四套卡通角色 + t99 四套抽象极简；选择 UI 由 t97 负责）。
 *
 * **封闭联合**：新增主题必须同时补 `LOGIN_THEME_IDS`、`LOGIN_THEME_LABEL_KEYS`、
 * `LOGIN_THEME_SUBTITLE_KEYS`（后两者是 `Record<LoginThemeId, …>`，漏补 tsc 直接报错）。
 */
export type LoginThemeId =
  | 'pencil'
  | 'geometric'
  | 'owl'
  | 'pixel-eyes'
  | 'aurora'
  | 'mono-grid'
  | 'orbit'
  | 'big-type';

/**
 * 主题分组：
 * - `character`：t95 的卡通角色，占左侧角色位（小尺寸 SVG，会眨眼/遮眼）；
 * - `abstract`：t99 的抽象极简主题，**不做角色**，铺满整页做背景层（全幅）。
 *
 * t97 的偏好弹窗按 group 分组渲染，并据 group 决定是否把动画实例塞进去
 * （抽象主题只用 palette 缩略图，不挂动画实例）。
 */
export type LoginThemeGroup = 'character' | 'abstract';

/**
 * 所有登录主题（角色 / 抽象）的统一 props 约定。
 *
 * 组件必须是**纯受控**的：不自己读路由/localStorage，只按 props 渲染，
 * `theme` 由外层（Login 页 / t97 的选择器）决定。
 */
export interface LoginCharacterProps {
  /** 焦点在密码框：角色遮眼 / 抽象主题收拢、淡出、加速、下沉（降噪） */
  passwordFocused: boolean;
  /** 登录失败：角色抖动一次 / 抽象主题整体 1px 位移一次 */
  hasError?: boolean;
  /** 尺寸上限（px）。抽象主题铺满整页，忽略该值 */
  size?: number;
  className?: string;
}

export const LOGIN_THEME_IDS: LoginThemeId[] = [
  'pencil',
  'geometric',
  'owl',
  'pixel-eyes',
  'aurora',
  'mono-grid',
  'orbit',
  'big-type',
];

/** 抽象极简主题（t99）：铺满整页做背景层，不占角色位 */
export const ABSTRACT_LOGIN_THEME_IDS: LoginThemeId[] = ['aurora', 'mono-grid', 'orbit', 'big-type'];

export const DEFAULT_LOGIN_THEME: LoginThemeId = 'pencil';

/** 主题 id → i18n 标签（供 t97 的选择器复用） */
export const LOGIN_THEME_LABEL_KEYS: Record<LoginThemeId, MessageKey> = {
  pencil: 'login.theme.pencil',
  geometric: 'login.theme.geometric',
  owl: 'login.theme.owl',
  'pixel-eyes': 'login.theme.pixelEyes',
  aurora: 'login.theme.aurora',
  'mono-grid': 'login.theme.monoGrid',
  orbit: 'login.theme.orbit',
  'big-type': 'login.theme.bigType',
};

/** 主题 id → 一句话说明（t97 卡片副标题；`Record` 保证八套都有，不漏） */
export const LOGIN_THEME_SUBTITLE_KEYS: Record<LoginThemeId, MessageKey> = {
  pencil: 'login.theme.pencil.subtitle',
  geometric: 'login.theme.geometric.subtitle',
  owl: 'login.theme.owl.subtitle',
  'pixel-eyes': 'login.theme.pixelEyes.subtitle',
  aurora: 'login.theme.aurora.subtitle',
  'mono-grid': 'login.theme.monoGrid.subtitle',
  orbit: 'login.theme.orbit.subtitle',
  'big-type': 'login.theme.bigType.subtitle',
};

/** 主题 id → 分组（t97 的分组标题：角色 / 抽象） */
export const LOGIN_THEME_GROUPS: Record<LoginThemeId, LoginThemeGroup> = {
  pencil: 'character',
  geometric: 'character',
  owl: 'character',
  'pixel-eyes': 'character',
  aurora: 'abstract',
  'mono-grid': 'abstract',
  orbit: 'abstract',
  'big-type': 'abstract',
};

export const isLoginThemeId = (value?: string | null): value is LoginThemeId =>
  Boolean(value && (LOGIN_THEME_IDS as string[]).includes(value));

/** 是否抽象主题（无角色、整页背景）：登录页据此切换布局 */
export const isAbstractThemeId = (id: LoginThemeId): boolean =>
  (ABSTRACT_LOGIN_THEME_IDS as LoginThemeId[]).includes(id);

/** 允许用 `?theme=orbit` 直接深链某套主题（t95/t99 用于验证；t97 的选择器可复用同一入口） */
export const resolveLoginThemeId = (value?: string | null): LoginThemeId =>
  isLoginThemeId(value) ? value : DEFAULT_LOGIN_THEME;
