import type { ReactNode, CSSProperties } from 'react';

/**
 * Modal that gates a destructive action. The confirm button is always danger,
 * the cancel one always ghost — the button that undoes cannot look like the one
 * that executes.
 * @startingPoint section="Feedback" subtitle="Confirmation — destructive action" viewport="520x260"
 */
export interface AppConfirmationDialogProps {
  /** Mono 13. Names the action, as a question. */
  title: ReactNode;
  /** Sans 12. The consequence: what goes away, and what does not come back. */
  message: ReactNode;
  /** Verb of the destructive action, never a bare "OK". */
  confirmLabel: string;
  cancelLabel: string;
  onConfirm?: () => void;
  onDismiss?: () => void;
  style?: CSSProperties;
}

export function AppConfirmationDialog(props: AppConfirmationDialogProps): JSX.Element;
