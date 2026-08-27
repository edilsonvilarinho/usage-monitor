import type { CSSProperties } from 'react';

/**
 * 2px vertical stroke that identifies which integration a panel or row belongs to.
 * Replaces the old full-card colored background: the accent stays, the area shrinks.
 * @startingPoint section="Core" subtitle="2px integration marker + legend dot" viewport="700x120"
 */
export interface AppSourceMarkProps {
  source?: 'anthropic' | 'codex' | 'deepseek' | 'minimax' | 'opencode' | 'kilo' | 'neutral';
  /** Escape hatch for team-member series colors. Prefer `source`. */
  color?: string;
  style?: CSSProperties;
}

export interface AppSourceDotProps extends AppSourceMarkProps {
  size?: number;
}

export function AppSourceMark(props: AppSourceMarkProps): JSX.Element;
export function AppSourceDot(props: AppSourceDotProps): JSX.Element;
