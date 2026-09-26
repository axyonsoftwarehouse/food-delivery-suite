import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import type { OrderStatus } from '../api/types';
import { theme } from '../theme';

const COLORS: Record<OrderStatus, string> = {
  placed: theme.warning,
  accepted: theme.primary,
  ready: theme.success,
  assigned: theme.info,
  picked_up: theme.info,
  served: theme.info,
  completed: theme.success,
  delivered: theme.success,
  rejected: theme.danger,
  cancelled: theme.danger,
  expired: theme.textMuted,
  failed: theme.danger,
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { t } = useTranslation();
  return (
    <View style={[styles.badge, { backgroundColor: COLORS[status] }]}>
      <Text style={styles.text}>{t(`status.${status}`)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
    alignSelf: 'flex-start',
  },
  text: {
    color: '#0F172A',
    fontSize: 12,
    fontWeight: '700',
  },
});
