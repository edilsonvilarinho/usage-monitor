import type { ReactNode, CSSProperties } from 'react';

/**
 * Single-line text input: session filters, server URL, nickname, API keys.
 * Background is --bg (recessed against the panel), mono 12, focus ring in --info.
 * @startingPoint section="Forms" subtitle="Text field — bare, labelled, with hint" viewport="700x180"
 */
export interface AppTextFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  /** Mono 10 uppercase eyebrow above the field. */
  label?: ReactNode;
  /** Sans 12 explanation below — this is where the "why" goes. */
  hint?: ReactNode;
  disabled?: boolean;
  type?: 'text' | 'password' | 'url' | 'number';
  style?: CSSProperties;
}

export function AppTextField(props: AppTextFieldProps): JSX.Element;
