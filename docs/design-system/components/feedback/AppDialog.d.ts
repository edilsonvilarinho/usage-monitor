import type { ReactNode, CSSProperties } from 'react';

/**
 * Dialog inside a window: a short question with the action it proposes and one
 * that gives up. Replaces Material's AlertDialog. The scrim fades in and the
 * card enters with fade + scale 0.96 → 1 (GENTLE spring); the exit is dry.
 * @startingPoint section="Feedback" subtitle="Dialog — in-window question" viewport="520x260"
 */
export interface AppDialogProps {
  /** Rendered only while true. */
  open?: boolean;
  /** Mono 13. The question. */
  title?: ReactNode;
  /** Sans 12, muted. Scrolls above 280px in the app so the buttons stay on screen. */
  children?: ReactNode;
  /** Dismiss first, then the proposed action — right-aligned. At most one primary. */
  actions: ReactNode;
  /** Clicking the scrim (and Esc in the app) asks to close. */
  onDismiss?: () => void;
  style?: CSSProperties;
}

export function AppDialog(props: AppDialogProps): JSX.Element | null;
