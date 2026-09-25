import type { CSSProperties } from 'react';

/**
 * Provider identification mark — Claude asterisk, OpenAI knot, Cursor cube,
 * Antigravity arch… Monochrome, tinted by the caller. Decorative: the provider
 * name is always written beside it. Compose: `AppProviderMark`.
 */
export interface AppProviderMarkProps {
  source: 'anthropic' | 'codex' | 'cursor' | 'antigravity' | 'gemini' | 'kilo' | 'oc' | 'opencode' | 'deepseek' | 'minimax' | 'openrouter';
  size?: number;
  color?: string;
  style?: CSSProperties;
}

export declare function AppProviderMark(props: AppProviderMarkProps): JSX.Element | null;
