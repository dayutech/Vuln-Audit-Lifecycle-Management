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

/**
 * 主题舞台的明暗（t108）：登录页左上角徽标据此选配色 ——
 * 浅色舞台用三色 `color` 版，深色舞台用单色 `monochrome` 版（保证任何底色上都可读）。
 */
export type LoginThemeTone = 'light' | 'dark';

/**
 * 登录框输入框形态（t101）：
 * - `outline`：描边框（角色向、部分抽象向）；
 * - `underline`：无边框、仅下划线（抽象向的极简气质）；
 * - `filled`：浅色填充、弱描边（角色向的柔和款）。
 */
export type LoginInputVariant = 'outline' | 'underline' | 'filled';

/**
 * 每套主题专属的登录框皮肤（t101）。
 *
 * 注册表 `themes.ts` 的每条 entry 都带一个；登录页把它换成 CSS 变量（`--skin-*`）挂到卡片上，
 * `loginStage.css` 统一消费 —— 主题之间只换数据、不换结构，避免八份重复样式。
 *
 * 可读性约束：`ink` / `inkSoft` 必须在 `cardBg` 上对比足够（深色卡片配浅色字，反之亦然）；
 * `buttonColor` 与 `buttonBg` 同理。这两组对比度会被 t101 的验证脚本逐主题量化检查。
 */
export interface LoginFormSkin {
  /** 卡片底色（允许半透明；深色主题用半透明 + 背景模糊，保住全屏主题的呼吸感） */
  cardBg: string;
  /** 卡片边框（含宽度，如 `2px solid #1f2328`） */
  cardBorder: string;
  /** 卡片圆角 */
  cardRadius: string;
  /** 卡片内边距 */
  cardPadding: string;
  /** 卡片投影（角色向允许一点点，抽象向基本为 none） */
  cardShadow?: string;
  /** 卡片背景模糊（半透明深色卡片用） */
  cardBackdrop?: string;
  /** 主文字色 */
  ink: string;
  /** 次要文字色（副标题 / 占位符 / 图标） */
  inkSoft: string;
  /** 输入框描边色 */
  inputBorder: string;
  /** 输入框填充色（`underline` 一般为 transparent） */
  inputBg: string;
  /** 输入框聚焦色 */
  inputFocus: string;
  /** 输入框形态 */
  inputVariant: LoginInputVariant;
  /** 表单标签字重 */
  labelWeight: number;
  /** 按钮圆角 */
  buttonRadius: string;
  /** 按钮底色 */
  buttonBg: string;
  /** 按钮文字色 */
  buttonColor: string;
}

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
