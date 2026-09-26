import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSession } from '../auth/session';
import { BoardColumn } from '../components/BoardColumn';
import { COLUMN_ORDER, groupByColumn } from '../domain/orders';
import { useNow } from '../hooks/useNow';
import type { RootStackParamList } from '../navigation';
import { useOrders, useOrderStatus } from '../query';
import { theme } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Board'>;

export default function BoardScreen({ navigation }: Props) {
  const { t } = useTranslation();
  const now = useNow(30_000);
  const user = useSession((state) => state.user);
  const signOut = useSession((state) => state.signOut);
  const { data, isLoading, isError, error, isFetching, refetch, dataUpdatedAt } = useOrders();
  const status = useOrderStatus();

  const grouped = groupByColumn(data ?? []);
  const busyOrderId = status.isPending ? status.variables?.orderId : undefined;

  return (
    <View style={styles.container}>
      <View style={styles.bar}>
        <View style={styles.barInfo}>
          <Text style={styles.restaurant}>{user?.name ?? 'Cozinha'}</Text>
          <Text style={styles.sync}>
            {isFetching
              ? '...'
              : t('board.lastSync', {
                  time: dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString('pt-BR') : '-',
                })}
          </Text>
        </View>
        {isError && (
          <Text style={styles.error}>{error instanceof Error ? error.message : t('board.offline')}</Text>
        )}
        <View style={styles.barActions}>
          <Pressable onPress={() => refetch()} style={styles.barButton}>
            <Text style={styles.barButtonText}>{t('board.refresh')}</Text>
          </Pressable>
          <Pressable onPress={() => signOut()} style={[styles.barButton, styles.signOut]}>
            <Text style={styles.barButtonText}>{t('board.signOut')}</Text>
          </Pressable>
        </View>
      </View>

      {isLoading ? (
        <ActivityIndicator color={theme.primary} style={styles.loading} />
      ) : (
        <View style={styles.columns}>
          {COLUMN_ORDER.map((key) => (
            <BoardColumn
              key={key}
              columnKey={key}
              orders={grouped[key]}
              now={now}
              busyOrderId={busyOrderId}
              onOpen={(id) => navigation.navigate('Ticket', { orderId: id })}
              onAccept={(id) => status.mutate({ orderId: id, action: 'accept' })}
              onReady={(id) => status.mutate({ orderId: id, action: 'ready' })}
              onServe={(id) => status.mutate({ orderId: id, action: 'serve' })}
              onComplete={(id) => status.mutate({ orderId: id, action: 'complete' })}
              onReject={(id) => navigation.navigate('Ticket', { orderId: id })}
            />
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.bg },
  bar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 10, backgroundColor: theme.surface, gap: 12 },
  barInfo: { flexShrink: 1 },
  restaurant: { color: theme.text, fontSize: 18, fontWeight: '800' },
  sync: { color: theme.textMuted, fontSize: 12 },
  error: { color: theme.danger, flexShrink: 1 },
  barActions: { flexDirection: 'row', gap: 8 },
  barButton: { backgroundColor: theme.surfaceAlt, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8 },
  signOut: { backgroundColor: theme.danger },
  barButtonText: { color: theme.text, fontWeight: '700' },
  columns: { flex: 1, flexDirection: 'row' },
  loading: { flex: 1 },
});
