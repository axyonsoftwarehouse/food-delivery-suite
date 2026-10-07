'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import PaymentAccountCard from '../payment-account-card';
import { useApp } from '../../app-context';

export default function ConfiguracoesPage() {
  const router = useRouter();
  const { user } = useApp();
  const [retornoPagamento, setRetornoPagamento] = useState(false);

  useEffect(() => {
    // O callback do Mercado Pago volta para /painel/configuracoes?mercadopago=...
    // Nesse caso o card trata a confirmação; nas demais, cai na seção Conta.
    if (new URLSearchParams(window.location.search).get('mercadopago')) {
      setRetornoPagamento(true);
      return;
    }
    router.replace('/painel/configuracoes/conta');
  }, [router]);

  if (retornoPagamento && user?.role === 'restaurant') return <PaymentAccountCard />;
  return null;
}
