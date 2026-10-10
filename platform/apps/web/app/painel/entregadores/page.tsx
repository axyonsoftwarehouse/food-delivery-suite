'use client';

import Link from 'next/link';
import { useApp } from '../../app-context';
import CourierMap from '../courier-map';
import { distanceLabel, statusLabel, updatedAgo, useCourierBoard } from '../courier-board';

/** Entregadores agora (parte D): mapa com a loja e quem tem posição recente, e a lista do quadro. */
export default function EntregadoresPage() {
  const { user, permissions } = useApp();
  const allowed = user?.role === 'restaurant' && permissions.includes('orders.dispatch');
  const { board, failed } = useCourierBoard(allowed);
  if (!user) return null;
  if (!allowed) return <section className="panel"><div className="empty-state">{'Você não tem permissão para despachar pedidos.'}</div></section>;
  if (!board) return <section className="panel"><p role="status">{failed ? 'Não foi possível carregar os entregadores.' : 'Carregando entregadores…'}</p></section>;
  const hasStore = board.restaurant.latitude != null && board.restaurant.longitude != null;
  return <section className="panel courier-board">
    {failed && <p className="courier-map-note" role="status">{'Sem conexão — mostrando a última atualização.'}</p>}
    {hasStore
      ? <CourierMap board={board} />
      : <p className="courier-map-note">{'Cadastre o endereço da loja para ver o mapa. '}<Link href="/painel/configuracoes/loja">{'Abrir Configurações'}</Link></p>}
    {board.couriers.length === 0
      ? <div className="empty-state">{'Nenhum entregador cadastrado. Cadastre em Equipe e acessos.'}</div>
      : <ul className="courier-board-list">{board.couriers.map((courier) => <li key={courier.id}>
          <span><span className={`board-dot is-${courier.status}`} aria-hidden="true" /><strong>{courier.name}</strong>{courier.suggested ? ' · Mais perto' : ''}</span>
          <small>{[statusLabel(courier), distanceLabel(courier.distanceMeters), courier.locationUpdatedAt ? updatedAgo(courier.locationUpdatedAt) : null].filter(Boolean).join(' · ')}</small>
        </li>)}</ul>}
  </section>;
}
