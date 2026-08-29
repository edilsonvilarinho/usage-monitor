import type { ReactNode, CSSProperties } from 'react';

/**
 * Multi-line text input: the bug report description, the only prose field in the app.
 * Background is --bg (recessed against the panel), sans 12, focus ring in --info,
 * 96px minimum height, content anchored to the top.
 * @startingPoint section="Forms" subtitle="Text area — bare, labelled, with hint" viewport="700x260"
 */
export interface AppTextAreaProps {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  /** Mono 10 uppercase eyebrow above the field. */
  label?: ReactNode;
  /** Sans 12 explanation below — this is where the "why" goes. */
  hint?: ReactNode;
  disabled?: boolean;
  style?: CSSProperties;
}

export function AppTextArea(props: AppTextAreaProps): JSX.Element;
