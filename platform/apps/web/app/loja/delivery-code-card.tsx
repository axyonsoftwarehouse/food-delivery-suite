'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';

/** Código de 4 dígitos que o cliente passa ao entregador ao receber o pedido (entregador, parte B). */
export default function DeliveryCodeCard({ orderId }: { orderId: number }) {
  const [code, setCode] = useState<string | null>(null);
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;
    const load = () => api<{ code: string }>(`/orders/${orderId}/delivery-code`)
      .then((data) => { setCode(data.code); if (timer) clearInterval(timer); })
      .catch(() => undefined);
    void load();
    timer = setInterval(() => void load(), 8000);
    return () => { if (timer) clearInterval(timer); };
  }, [orderId]);
  if (!code) return null;
  return <div className="orders-code" role="note">
    <small>{'Código de entrega'}</small>
    <strong aria-label={code.split('').join(' ')}>{code}</strong>
    <span>{'Informe ao entregador somente quando receber o pedido.'}</span>
  </div>;
}
