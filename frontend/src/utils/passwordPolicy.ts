/**
 * 密码强度判定（t105）—— 与后端 `com.vulnaudit.util.PasswordPolicy` **逐条对齐**：
 *
 * - 长度 8–64；
 * - 「大写 / 小写 / 数字 / 符号」四类中至少满足 **3 类**。
 *
 * 只用于前端实时提示与提交按钮启用/禁用；**最终校验以后端为准**（后端 400 优先展示）。
 * 单独抽成纯函数便于在 UI 之外复用与自查。
 */

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 64;
export const PASSWORD_MIN_CLASSES = 3;

export interface PasswordAssessment {
  /** 实际长度 */
  length: number;
  /** 长度是否落在 8–64 */
  longEnough: boolean;
  hasUpper: boolean;
  hasLower: boolean;
  hasDigit: boolean;
  hasSymbol: boolean;
  /** 命中了几类字符（0–4） */
  classes: number;
  /** 四段强度条点亮的段数（0–4） */
  segments: number;
  /** 是否满足后端策略（提交按钮据此启用） */
  valid: boolean;
}

const UPPER = /[A-Z]/;
const LOWER = /[a-z]/;
const DIGIT = /\d/;
/** 符号：非字母数字（与后端的 `[^A-Za-z0-9]` 完全一致） */
const SYMBOL = /[^A-Za-z0-9]/;

export const evaluatePassword = (raw: string | null | undefined): PasswordAssessment => {
  const value = raw ?? '';
  const length = value.length;
  const longEnough = length >= PASSWORD_MIN_LENGTH && length <= PASSWORD_MAX_LENGTH;
  const hasUpper = UPPER.test(value);
  const hasLower = LOWER.test(value);
  const hasDigit = DIGIT.test(value);
  const hasSymbol = SYMBOL.test(value);
  const classes = [hasUpper, hasLower, hasDigit, hasSymbol].filter(Boolean).length;

  const valid = longEnough && classes >= PASSWORD_MIN_CLASSES;

  /**
   * 四段强度：
   * 1. 长度达标；
   * 2. 已命中两类字符；
   * 3. 命中三类（= 达标线）；
   * 4. 四类齐全且长度 ≥12（很强）。
   */
  let segments = 0;
  if (longEnough) segments = 1;
  if (longEnough && classes >= 2) segments = 2;
  if (valid) segments = 3;
  if (valid && classes === 4 && length >= 12) segments = 4;

  return { length, longEnough, hasUpper, hasLower, hasDigit, hasSymbol, classes, segments, valid };
};

/** 强度等级（用于文案 key：passwordStrength.level.*） */
export const passwordStrengthLevel = (assessment: PasswordAssessment): 'weak' | 'fair' | 'good' | 'strong' => {
  if (assessment.segments >= 4) return 'strong';
  if (assessment.segments >= 3) return 'good';
  if (assessment.segments >= 2) return 'fair';
  return 'weak';
};
