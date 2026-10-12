'use client';

import { useCallback, type ReactElement } from 'react';
import { useReasonDialog } from './ReasonDialog';
import type { AskReason } from './support-request';

const REASON_ACTION_LABELS: Record<string, string> = { post: 'Criar registro', put: 'Alterar registro', patch: 'Alterar registro', delete: 'Excluir registro' };

/** Motivo das escritas do modo suporte (E48): mesmo diálogo do painel, com o aviso de que a loja é avisada. */
export function useSupportReason(): { askReason: AskReason; dialog: ReactElement | null } {
  const { ask, dialog } = useReasonDialog();
  const askReason = useCallback<AskReason>((next) => ask({
    title: 'Motivo da intervenção',
    action: next.action ?? REASON_ACTION_LABELS[next.method.toLowerCase()] ?? 'Alterar registro',
    warning: 'Esta alteração será feita em nome da loja, registrada na trilha e avisada à loja.',
    min: 10,
    max: 500,
    label: 'Motivo (10 a 500 caracteres)',
    confirmLabel: 'Confirmar intervenção',
    cancelLabel: 'Cancelar',
    draftKey: `${next.method} ${next.action ?? ''}`,
  }), [ask]);
  return { askReason, dialog };
}
