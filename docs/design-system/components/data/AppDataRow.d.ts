import type { ReactNode, CSSProperties } from 'react';

/**
 * One line of data inside a flush panel body. Owns its own bottom divider —
 * which is why a list of these needs no gap and the nested guide comes out continuous.
 * @startingPoint section="Data" subtitle="Data row + key/value type pair" viewport="700x220"
 */
export interface AppDataRowProps {
  children?: ReactNode;
  /** An <AppSourceMark> for rows that mix integrations. */
  mark?: ReactNode;
  onClick?: () => void;
  /** Extra left padding in px, for a child row. */
  indent?: number;
  /**
   * Draws the 2dp nested-group guide: this row is a sibling in the list but
   * belongs to the row above it. Children of an expanded row are siblings,
   * never a nested list — nesting breaks LazyColumn scrolling and item reuse.
   * Rests on `--surface`, one rung above the window's `--bg` — matches
   * `Modifier.appNestedGroupItem`, the real Compose implementation. Hover
   * still lifts to `--raised` when `hoverable`; `guide` changes the resting
   * color, never the interaction (fixed 2026-09-04, issue #223 — it used to
   * pin the row to `--bg` and disable hover outright).
   */
  guide?: boolean;
  last?: boolean;
  hoverable?: boolean;
  style?: CSSProperties;
}

export interface AppKeyProps { children?: ReactNode; dim?: boolean; style?: CSSProperties; }
export interface AppValueProps { children?: ReactNode; size?: 'sm' | 'md' | 'lg'; dim?: boolean; style?: CSSProperties; }

export function AppDataRow(props: AppDataRowProps): JSX.Element;
export function AppKey(props: AppKeyProps): JSX.Element;
export function AppValue(props: AppValueProps): JSX.Element;
