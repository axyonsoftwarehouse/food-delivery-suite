'use client';

import WalletPanel from '../wallet-panel';
import { useApp } from '../../app-context';

export default function CarteiraPage() {
  const { user } = useApp();
  if (!user || user.role !== 'courier') {
    return <section className="panel"><div className="empty-state">Carteira disponível para entregadores.</div></section>;
  }
  return <WalletPanel />;
}
