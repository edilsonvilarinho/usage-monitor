import * as React from 'react';

export interface AppGroupBandProps {
  /** Rótulo da faixa. Mono 10, cor `--muted`. */
  label: string;
  /** Segunda linha, opcional. Mesma escala e mesma cor do rótulo. */
  detail?: string;
  /** Recuo do nível, somado ao padding horizontal. */
  indent?: number;
  /** Padding horizontal da lista em que a faixa vive. */
  horizontalPadding?: number;
  /** Ação à direita — normalmente um `AppIconButton`. */
  trailing?: React.ReactNode;
  style?: React.CSSProperties;
}

export declare function AppGroupBand(props: AppGroupBandProps): JSX.Element;
