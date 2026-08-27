import type { ReactNode, CSSProperties } from 'react';

/**
 * 30x17 toggle for a boolean setting. On = --ok, because "on" here means a
 * background job is actually running.
 * @startingPoint section="Forms" subtitle="Switch — on, off, disabled with reason" viewport="700x190"
 */
export interface AppSwitchProps {
  checked?: boolean;
  onChange?: (next: boolean) => void;
  /** Omit to render the bare track (settings tables that own their own labels). */
  label?: ReactNode;
  hint?: ReactNode;
  disabled?: boolean;
  /**
   * MANDATORY when disabled: why this setting does not apply here (macOS DMG has
   * no Developer ID; Linux .deb belongs to the package manager; …). Rendered in
   * place of the hint and as the tooltip.
   */
  reason?: ReactNode;
  style?: CSSProperties;
}

export function AppSwitch(props: AppSwitchProps): JSX.Element;
