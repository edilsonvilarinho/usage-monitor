import type { ReactNode, CSSProperties } from 'react';

/**
 * Dropdown menu anchored to a control: picks **one** option from a short list.
 *
 * It is `AppSegmentedControl`'s sibling for when the options do not fit on the
 * bar. The segmented control shows every option all the time and costs the
 * width of all of them; the menu shows the current one and the rest on demand.
 * On a 30dp status bar already carrying up to five actions, three window-mode
 * labels side by side do not fit — which is why this primitive exists.
 *
 * **Not the platform's own menu component.** That one brings its own surface,
 * radius, entry animation and item height, and none of the four belong to this
 * system; dressing it from the outside would leave two menu designs in the same
 * app, one of them invisible in the code. The anatomy here is the usual one:
 * clip, `surface` background, the 1dp border, and the *raised* elevation — the
 * step the token itself describes as "tooltip and dropdown menu".
 *
 * **The selected item carries a mark as well as the highlight.** The container
 * is the same one the segmented control and the settings nav item use — there
 * is no second selection design in this app — and the mark beside the label is
 * what keeps color from stating the choice on its own. The mark's column is
 * reserved on every row, or the selected label would shift sideways whenever
 * the choice moved.
 *
 * **It opens upwards when it does not fit below.** Today's consumer lives in
 * the footer, the window's last row: a menu that only knew how to open
 * downwards would be born outside the window. A popup on this platform is a
 * layer *inside* the window, clipped to its bounds, so the position is pinned
 * to the window on both axes.
 * @startingPoint section="Core" subtitle="Dropdown menu — one choice, on demand" viewport="700x220"
 */
export interface AppMenuProps {
  /** The menu is showing. The open/closed state belongs to the host. */
  open?: boolean;
  options?: Array<{ id: string; label: string } | string>;
  /** Id of the current option — marked, not merely highlighted. */
  value?: string;
  onSelect?: (id: string) => void;
  /** Click outside or Escape. */
  onDismiss?: () => void;
  /** Which side it opens to when there is room; it flips when there is not. */
  placement?: 'top' | 'bottom';
  /** The anchor control — usually an `AppIconButton`. */
  children?: ReactNode;
  style?: CSSProperties;
}

export function AppMenu(props: AppMenuProps): JSX.Element;
