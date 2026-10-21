export type SubscriptionCharge = {
  id: number;
  subscription_id?: number;
  method: string;
  amount_cents: number;
  status: string;
  raw_status?: string | null;
  period_start: string | null;
  period_end: string | null;
  paid_at?: string | null;
  expires_at?: string | null;
  created_at?: string | null;
  qr_code?: string | null;
  qr_code_base64?: string | null;
  ticket_url?: string | null;
  challenge_url?: string | null;
};

type BadgeTone = 'brand' | 'warning' | 'danger' | 'info' | 'neutral';

const SUBSCRIPTION_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  trial: { label: 'Em teste', tone: 'info' },
  active: { label: 'Ativa', tone: 'brand' },
  suspended: { label: 'Em atraso', tone: 'warning' },
  expired: { label: 'Expirada', tone: 'danger' },
  cancelled: { label: 'Cancelada', tone: 'neutral' },
  paused: { label: 'Pausada', tone: 'warning' },
};

const CHARGE_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  pending: { label: 'Aguardando pagamento', tone: 'warning' },
  paid: { label: 'Paga', tone: 'brand' },
  expired: { label: 'Expirada', tone: 'neutral' },
  cancelled: { label: 'Cancelada', tone: 'neutral' },
  refunded: { label: 'Estornada', tone: 'info' },
  failed: { label: 'Falhou', tone: 'danger' },
};

const METHODS: Record<string, string> = { pix: 'Pix', card: 'Cartão' };

export function subscriptionStatus(status: string | null | undefined) {
  return status ? SUBSCRIPTION_STATUS[status] ?? { label: status, tone: 'neutral' as BadgeTone } : null;
}

export function chargeStatus(status: string | null | undefined) {
  return status ? CHARGE_STATUS[status] ?? { label: status, tone: 'neutral' as BadgeTone } : null;
}

export function methodLabel(method: string | null | undefined) {
  return method ? METHODS[method] ?? method : '—';
}

export function day(value: string | null | undefined) {
  return value ? new Date(value).toLocaleDateString('pt-BR') : '—';
}

export function dateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('pt-BR') : '—';
}

export function period(start: string | null | undefined, end: string | null | undefined) {
  if (!start && !end) return '—';
  return `${day(start)} a ${day(end)}`;
}

export function hasPix(charge: SubscriptionCharge | null | undefined) {
  return Boolean(charge && (charge.qr_code || charge.qr_code_base64 || charge.ticket_url));
}

export function daysUntil(value: string | null | undefined) {
  if (!value) return null;
  const diff = new Date(value).getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / 86_400_000));
}

export function badgeClass(tone: BadgeTone) {
  return tone === 'neutral' ? 'pr-badge' : `pr-badge is-${tone}`;
}
