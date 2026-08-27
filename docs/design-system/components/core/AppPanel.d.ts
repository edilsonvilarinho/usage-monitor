import type { ReactNode, CSSProperties } from 'react';

/**
 * The data surface: surface fill, 1dp border, radius 8, zero elevation.
 * Every group of data on every screen sits in one of these.
 * @startingPoint section="Core" subtitle="Panel + header + body — the data surface" viewport="700x260"
 */
export interface AppPanelProps {
  children?: ReactNode;
  style?: CSSProperties;
}

export interface AppPanelHeaderProps {
  title: ReactNode;
  /** Second line — account e-mail, org, machine. Mono 10, muted. */
  subtitle?: ReactNode;
  /** An <AppSourceMark> when the panel belongs to one integration. */
  mark?: ReactNode;
  /** An <AppStatusIndicator> — sits right of the spacer, left of the actions. */
  status?: ReactNode;
  /** One or more <AppIconButton>. */
  actions?: ReactNode;
  style?: CSSProperties;
}

export interface AppPanelBodyProps {
  children?: ReactNode;
  /** No padding, no gap — for a stack of <AppDataRow> that own their own dividers. */
  flush?: boolean;
  /** --s2/--s3 padding — the dashboard window density. */
  dense?: boolean;
  style?: CSSProperties;
}

export function AppPanel(props: AppPanelProps): JSX.Element;
export function AppPanelHeader(props: AppPanelHeaderProps): JSX.Element;
export function AppPanelBody(props: AppPanelBodyProps): JSX.Element;
