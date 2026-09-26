import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import type { StatusAction } from '../api/types';
import { useSession } from '../auth/session';
import { StatusBadge } from '../components/StatusBadge';
import { canComplete, canServe, elapsedMinutes, formatMoney } from '../domain/orders';
import { useNow } from '../hooks/useNow';
import type { RootStackParamList } from '../navigation';
import { printTicket } from '../printing/ticket';
import { useOrder, useOrderStatus } from '../query';
import { theme } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Ticket'>;

export default function TicketScreen({ route, navigation }: Props) {
  const { t } = useTranslation();
  const orderId = route.params.orderId;
  const now = useNow(15_000);
  const { data: order, isLoading, isError, error } = useOrder(orderId);
  const status = useOrderStatus();
  const user = useSession((state) => state.user);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [printing, setPrinting] = useState(false);
  const [printError, setPrintError] = useState<string | null>(null);

  if (isLoading) return <ActivityIndicator color={theme.primary} style={styles.loading} />;
  if (isError || !order) {
    return (
      <View style={styles.centered}>
        <Text style={styles.error}>{error instanceof Error ? error.message : t('board.offline')}</Text>
      </View>
    );
  }

  const minutes = elapsedMinutes(order.created_at, now);
  const canAccept = order.status === 'placed';
  const canReady = order.status === 'accepted';
  const canServeOrder = canServe(order);
  const canCompleteOrder = canComplete(order);
  const currentOrder = order;

  function runAction(action: StatusAction, extra?: string) {
    status.mutate(
      { orderId, action, reason: extra },
      { onSuccess: () => navigation.goBack() },
    );
  }

  async function handlePrint() {
    setPrinting(true);
    setPrintError(null);
    try {
      await printTicket(currentOrder, { restaurantName: user?.name });
    } catch (cause) {
      setPrintError(cause instanceof Error ? cause.message : t('ticket.printError'));
    } finally {
      setPrinting(false);
    }
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.id}>#{order.id}</Text>
        <StatusBadge status={order.status} />
      </View>
      <Text style={styles.meta}>
        {minutes === null ? '' : t('board.minutes', { count: minutes })} • {formatMoney(order.total_cents)}
      </Text>
      {order.order_type !== 'delivery' && (
        <Text style={styles.section}>
          {order.order_type === 'dine_in'
            ? t('board.table', { number: order.table_number ?? order.table_id ?? '' })
            : t('board.takeAway')}
        </Text>
      )}

      <Text style={styles.section}>{t('ticket.items')}</Text>
      {order.items.map((item) => (
        <View key={item.id} style={styles.item}>
          <Text style={styles.itemQty}>{item.quantity}x</Text>
          <View style={styles.itemBody}>
            <Text style={styles.itemName}>{item.name}</Text>
            {item.variation_name && <Text style={styles.itemOption}>{item.variation_name}</Text>}
            {item.addons && <Text style={styles.itemOption}>+ {item.addons}</Text>}
          </View>
        </View>
      ))}

      <Text style={styles.section}>{t('ticket.address')}</Text>
      <Text style={styles.paragraph}>{order.delivery_address_text}</Text>

      {order.scheduled_at && (
        <>
          <Text style={styles.section}>{t('ticket.scheduled')}</Text>
          <Text style={styles.paragraph}>{new Date(order.scheduled_at).toLocaleString('pt-BR')}</Text>
        </>
      )}

      {status.isError && <Text style={styles.error}>{status.error instanceof Error ? status.error.message : t('board.offline')}</Text>}

      <View style={styles.actions}>
        {canAccept && (
          <Pressable disabled={status.isPending} onPress={() => runAction('accept')} style={[styles.button, styles.accept]}>
            <Text style={styles.buttonText}>{t('ticket.accept')}</Text>
          </Pressable>
        )}
        {canReady && (
          <Pressable disabled={status.isPending} onPress={() => runAction('ready')} style={[styles.button, styles.ready]}>
            <Text style={styles.buttonText}>{t('ticket.ready')}</Text>
          </Pressable>
        )}
        {canServeOrder && (
          <Pressable disabled={status.isPending} onPress={() => runAction('serve')} style={[styles.button, styles.ready]}>
            <Text style={styles.buttonText}>{t('ticket.serve')}</Text>
          </Pressable>
        )}
        {canCompleteOrder && (
          <Pressable disabled={status.isPending} onPress={() => runAction('complete')} style={[styles.button, styles.accept]}>
            <Text style={styles.buttonText}>{t('ticket.complete')}</Text>
          </Pressable>
        )}
        {canAccept && (
          <Pressable onPress={() => setRejecting((value) => !value)} style={[styles.button, styles.reject]}>
            <Text style={styles.buttonText}>{t('ticket.reject')}</Text>
          </Pressable>
        )}
        <Pressable disabled={printing} onPress={handlePrint} style={[styles.button, styles.print, printing && styles.disabled]}>
          <Text style={styles.buttonText}>{printing ? t('ticket.printing') : t('ticket.print')}</Text>
        </Pressable>
      </View>

      {printError && <Text style={styles.error}>{printError}</Text>}

      {rejecting && (
        <View style={styles.rejectBox}>
          <Text style={styles.section}>{t('ticket.rejectReason')}</Text>
          <TextInput
            value={reason}
            onChangeText={setReason}
            style={styles.input}
            placeholder="Ex.: item esgotado"
            placeholderTextColor={theme.textMuted}
            multiline
          />
          <Pressable
            disabled={reason.trim().length < 3 || status.isPending}
            onPress={() => runAction('reject', reason.trim())}
            style={[styles.button, styles.reject, (reason.trim().length < 3 || status.isPending) && styles.disabled]}
          >
            <Text style={styles.buttonText}>{t('ticket.send')}</Text>
          </Pressable>
        </View>
      )}

      <Text style={styles.section}>{t('ticket.history')}</Text>
      {order.history.map((event, index) => (
        <Text key={`${event.to_status}-${index}`} style={styles.history}>
          {new Date(event.created_at).toLocaleTimeString('pt-BR')} • {t(`status.${event.to_status}`, { defaultValue: event.to_status })}
          {event.reason ? ` — ${event.reason}` : ''}
        </Text>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.bg },
  content: { padding: 20, gap: 6 },
  loading: { flex: 1 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  id: { color: theme.text, fontSize: 28, fontWeight: '900' },
  meta: { color: theme.textMuted, fontSize: 14, marginBottom: 8 },
  section: { color: theme.textMuted, fontSize: 12, fontWeight: '800', textTransform: 'uppercase', marginTop: 16 },
  item: { flexDirection: 'row', gap: 10, marginTop: 8 },
  itemQty: { color: theme.primary, fontSize: 16, fontWeight: '800', minWidth: 32 },
  itemBody: { flex: 1 },
  itemName: { color: theme.text, fontSize: 16 },
  itemOption: { color: theme.textMuted, fontSize: 13 },
  paragraph: { color: theme.text, fontSize: 15 },
  actions: { flexDirection: 'row', gap: 10, marginTop: 20, flexWrap: 'wrap' },
  button: { borderRadius: 10, paddingHorizontal: 20, paddingVertical: 14, alignItems: 'center', minWidth: 140 },
  accept: { backgroundColor: theme.success },
  ready: { backgroundColor: theme.primary },
  reject: { backgroundColor: theme.danger },
  print: { backgroundColor: theme.surfaceAlt },
  disabled: { opacity: 0.5 },
  buttonText: { color: '#0F172A', fontWeight: '800', fontSize: 15 },
  rejectBox: { marginTop: 12, gap: 8 },
  input: { backgroundColor: theme.surface, color: theme.text, borderRadius: 8, padding: 12, borderWidth: 1, borderColor: theme.border, minHeight: 60 },
  error: { color: theme.danger, marginTop: 12 },
  history: { color: theme.textMuted, fontSize: 13, marginTop: 4 },
});
