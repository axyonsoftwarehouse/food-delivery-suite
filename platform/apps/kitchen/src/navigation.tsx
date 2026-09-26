import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useSession } from './auth/session';
import BoardScreen from './screens/BoardScreen';
import SignInScreen from './screens/SignInScreen';
import TicketScreen from './screens/TicketScreen';
import { theme } from './theme';

export type RootStackParamList = {
  SignIn: undefined;
  Board: undefined;
  Ticket: { orderId: number };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const token = useSession((state) => state.token);
  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.surface },
        headerTintColor: theme.text,
        contentStyle: { backgroundColor: theme.bg },
      }}
    >
      {token ? (
        <>
          <Stack.Screen name="Board" component={BoardScreen} options={{ title: 'Foodie Cozinha' }} />
          <Stack.Screen name="Ticket" component={TicketScreen} options={{ title: 'Pedido' }} />
        </>
      ) : (
        <Stack.Screen name="SignIn" component={SignInScreen} options={{ headerShown: false }} />
      )}
    </Stack.Navigator>
  );
}
