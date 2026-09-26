import { Platform } from 'react-native';
import { api } from './client';

export type PushPlatform = 'android' | 'ios' | 'web';

export function registerDeviceToken(
  token: string,
  deviceToken: string,
  platform: PushPlatform,
): Promise<{ ok: boolean }> {
  return api<{ ok: boolean }>('/notifications/device-tokens', {
    method: 'POST',
    token,
    body: { token: deviceToken, platform },
  });
}

export function unregisterDeviceToken(
  token: string,
  deviceToken: string,
  platform: PushPlatform,
): Promise<{ ok: boolean }> {
  return api<{ ok: boolean }>('/notifications/device-tokens', {
    method: 'DELETE',
    token,
    body: { token: deviceToken, platform },
  });
}

export function currentPlatform(): PushPlatform {
  if (Platform.OS === 'ios') return 'ios';
  if (Platform.OS === 'android') return 'android';
  return 'web';
}
