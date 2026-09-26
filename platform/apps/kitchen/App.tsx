import { NavigationContainer } from '@react-navigation/native';
import { QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import './src/i18n';
import { useSession } from './src/auth/session';
import { RootNavigator } from './src/navigation';
import { setupNotifications, subscribeNewOrders } from './src/notifications/push';
import { queryClient } from './src/query';

export default function App() {
  const hydrate = useSession((state) => state.hydrate);
  const hydrated = useSession((state) => state.hydrated);
  const token = useSession((state) => state.token);

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (token) void setupNotifications(token);
  }, [token]);

  useEffect(
    () =>
      subscribeNewOrders(() => {
        void queryClient.invalidateQueries({ queryKey: ['orders'] });
      }),
    [],
  );

  if (!hydrated) return null;

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <NavigationContainer>
          <RootNavigator />
        </NavigationContainer>
        <StatusBar style="light" />
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
