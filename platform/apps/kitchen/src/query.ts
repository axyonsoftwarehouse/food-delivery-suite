import { QueryClient, useMutation, useQuery } from '@tanstack/react-query';
import { getOrder, listOrders, changeStatus } from './api/orders';
import type { StatusAction } from './api/types';
import { useSession } from './auth/session';
import { POLL_INTERVAL_MS } from './config';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 0,
    },
  },
});

export function useOrders() {
  const token = useSession((state) => state.token);
  return useQuery({
    queryKey: ['orders'],
    queryFn: () => listOrders(token as string),
    enabled: Boolean(token),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
  });
}

export function useOrder(orderId: number) {
  const token = useSession((state) => state.token);
  return useQuery({
    queryKey: ['order', orderId],
    queryFn: () => getOrder(token as string, orderId),
    enabled: Boolean(token) && Number.isFinite(orderId),
  });
}

export function useOrderStatus() {
  const token = useSession((state) => state.token);
  return useMutation({
    mutationFn: ({ orderId, action, reason }: { orderId: number; action: StatusAction; reason?: string }) =>
      changeStatus(token as string, orderId, action, reason),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['order', variables.orderId] });
    },
  });
}
