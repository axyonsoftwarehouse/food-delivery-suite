import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import type { OrderListItem } from '../api/types';
import { elapsedMinutes, formatMoney } from '../domain/orders';
import { theme } from '../theme';
import { StatusBadge } from './StatusBadge';

type Props = {
  order: OrderListItem;
  now: number;
  late: boolean;
  busy?: boolean;
  onOpen: () => void;
  onAccept?: () => void;
  onReady?: () => void;
  onServe?: () => void;
  onComplete?: () => void;
  onReject?: () => void;
};

export function OrderCard({ order, now, late, busy, onOpen, onAccept, onReady, onServe, onComplete, onReject }: Props) {
  const { t } = useTranslation();
  const minutes = elapsedMinutes(order.created_at, now);

  return (
    <Pressable onPress={onOpen} style={[styles.card, late && styles.cardLate]}>
      <View style={styles.row}>
        <Text style={styles.id}>#{order.id}</Text>
        {late && <Text style={styles.late}>{t('board.late')}</Text>}
      </View>
      <View style={styles.row}>
        <StatusBadge status={order.status} />
        {order.order_type === 'dine_in' && (
          <Text style={styles.tag}>{t('board.table', { number: order.table_number ?? order.table_id ?? '' })}</Text>
        )}
        {order.order_type === 'take_away' && <Text style={styles.tag}>{t('board.takeAway')}</Text>}
      </View>
      <Text style={styles.address} numberOfLines={2}>
        {order.delivery_address_text}
      </Text>
      <View style={styles.row}>
        <Text style={styles.meta}>
          {minutes === null ? '' : t('board.minutes', { count: minutes })}
        </Text>
        <Text style={styles.total}>{formatMoney(order.total_cents)}</Text>
      </View>
      <View style={styles.actions}>
        {onAccept && (
          <Pressable disabled={busy} onPress={onAccept} style={[styles.action, styles.accept]}>
            <Text style={styles.actionText}>{t('ticket.accept')}</Text>
          </Pressable>
        )}
        {onReady && (
          <Pressable disabled={busy} onPress={onReady} style={[styles.action, styles.ready]}>
            <Text style={styles.actionText}>{t('ticket.ready')}</Text>
          </Pressable>
        )}
        {onServe && (
          <Pressable disabled={busy} onPress={onServe} style={[styles.action, styles.ready]}>
            <Text style={styles.actionText}>{t('ticket.serve')}</Text>
          </Pressable>
        )}
        {onComplete && (
          <Pressable disabled={busy} onPress={onComplete} style={[styles.action, styles.accept]}>
            <Text style={styles.actionText}>{t('ticket.complete')}</Text>
          </Pressable>
        )}
        {onReject && (
          <Pressable disabled={busy} onPress={onReject} style={[styles.action, styles.reject]}>
            <Text style={styles.actionText}>{t('ticket.reject')}</Text>
          </Pressable>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.surface,
    borderRadius: 10,
    padding: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 6,
  },
  cardLate: { borderColor: theme.danger },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  id: { color: theme.text, fontSize: 18, fontWeight: '800' },
  late: { color: theme.danger, fontSize: 12, fontWeight: '800' },
  tag: { color: theme.info, fontSize: 13, fontWeight: '700' },
  address: { color: theme.textMuted, fontSize: 13 },
  meta: { color: theme.textMuted, fontSize: 13 },
  total: { color: theme.text, fontSize: 14, fontWeight: '700' },
  actions: { flexDirection: 'row', gap: 8, marginTop: 4 },
  action: { flex: 1, borderRadius: 8, paddingVertical: 8, alignItems: 'center' },
  accept: { backgroundColor: theme.success },
  ready: { backgroundColor: theme.primary },
  reject: { backgroundColor: theme.danger },
  actionText: { color: '#0F172A', fontWeight: '800', fontSize: 13 },
});
