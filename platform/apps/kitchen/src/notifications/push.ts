import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { currentPlatform, registerDeviceToken } from '../api/notifications';

const CHANNEL_ID = 'kitchen-orders';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

function orderIdFromData(data: unknown): number | undefined {
  if (typeof data !== 'object' || data === null) return undefined;
  const value = (data as Record<string, unknown>).orderId;
  if (typeof value === 'number') return value;
  if (typeof value === 'string' && value.length > 0) {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? undefined : parsed;
  }
  return undefined;
}

export async function setupNotifications(sessionToken: string): Promise<string | null> {
  if (Platform.OS === 'web' || !Device.isDevice) return null;

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Pedidos da cozinha',
      importance: Notifications.AndroidImportance.MAX,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#F97316',
    });
  }

  const current = await Notifications.getPermissionsAsync();
  let granted = current.granted;
  if (!granted) {
    const requested = await Notifications.requestPermissionsAsync();
    granted = requested.granted;
  }
  if (!granted) return null;

  try {
    const device = await Notifications.getDevicePushTokenAsync();
    const token = typeof device.data === 'string' ? device.data : null;
    if (token) await registerDeviceToken(sessionToken, token, currentPlatform());
    return token;
  } catch {
    return null;
  }
}

export function subscribeNewOrders(onOrder: (orderId?: number) => void): () => void {
  const received = Notifications.addNotificationReceivedListener((notification) => {
    onOrder(orderIdFromData(notification.request.content.data));
  });
  const response = Notifications.addNotificationResponseReceivedListener((result) => {
    onOrder(orderIdFromData(result.notification.request.content.data));
  });
  return () => {
    received.remove();
    response.remove();
  };
}
