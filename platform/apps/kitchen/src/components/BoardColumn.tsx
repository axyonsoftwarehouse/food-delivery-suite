import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import type { OrderListItem } from '../api/types';
import { canComplete, canServe, isLate } from '../domain/orders';
import { theme } from '../theme';
import { OrderCard } from './OrderCard';

type Props = {
  columnKey: 'new' | 'preparing' | 'ready';
  orders: OrderListItem[];
  now: number;
  busyOrderId?: number;
  onOpen: (orderId: number) => void;
  onAccept: (orderId: number) => void;
  onReady: (orderId: number) => void;
  onServe: (orderId: number) => void;
  onComplete: (orderId: number) => void;
  onReject: (orderId: number) => void;
};

export function BoardColumn({ columnKey, orders, now, busyOrderId, onOpen, onAccept, onReady, onServe, onComplete, onReject }: Props) {
  const { t } = useTranslation();
  return (
    <View style={styles.column}>
      <View style={styles.header}>
        <Text style={styles.title}>{t(`board.${columnKey}`)}</Text>
        <Text style={styles.count}>{orders.length}</Text>
      </View>
      <FlatList
        data={orders}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={<Text style={styles.empty}>{t('board.empty')}</Text>}
        renderItem={({ item }) => {
          const isNew = columnKey === 'new';
          const isPreparing = columnKey === 'preparing';
          return (
            <OrderCard
              order={item}
              now={now}
              late={isLate(item, now)}
              busy={busyOrderId === item.id}
              onOpen={() => onOpen(item.id)}
              onAccept={isNew ? () => onAccept(item.id) : undefined}
              onReject={isNew ? () => onReject(item.id) : undefined}
              onReady={isPreparing ? () => onReady(item.id) : undefined}
              onServe={canServe(item) ? () => onServe(item.id) : undefined}
              onComplete={canComplete(item) ? () => onComplete(item.id) : undefined}
            />
          );
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  column: {
    flex: 1,
    backgroundColor: theme.bg,
    borderRightWidth: 1,
    borderRightColor: theme.surfaceAlt,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: theme.surfaceAlt,
  },
  title: { color: theme.text, fontSize: 16, fontWeight: '800' },
  count: { color: theme.text, fontSize: 16, fontWeight: '800' },
  list: { padding: 10 },
  empty: { color: theme.textMuted, textAlign: 'center', marginTop: 24 },
});
