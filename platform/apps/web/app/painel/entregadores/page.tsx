'use client';

import { useApp } from '../../app-context';
import CourierMap from '../courier-map';
import { distanceLabel, statusLabel, updatedAgo, useCourierBoard, type BoardCourier } from '../courier-board';
import { Card, Empty } from '../kit';

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();

function CourierRow({ courier }: { courier: BoardCourier }) {
  const details = [distanceLabel(courier.distanceMeters), courier.locationUpdatedAt ? updatedAgo(courier.locationUpdatedAt) : null].filter(Boolean).join(' · ');
  return <li className="pr-row">
    <span className={`vp-avatar is-${courier.status}`} aria-hidden="true">{initials(courier.name)}</span>
    <div className="pr-row-main">
      <span className="vp-name-line"><strong>{courier.name}</strong>{courier.suggested && <span className="pr-badge is-brand">{'Mais perto'}</span>}</span>
      <small>{details ? <>{statusLabel(courier)} · {details}</> : statusLabel(courier)}</small>
    </div>
  </li>;
}

/** Entregadores agora (parte D): mapa com a loja e quem tem posição recente, e a lista do quadro. */
export default function EntregadoresPage() {
  const { user, permissions } = useApp();
  const allowed = user?.role === 'restaurant' && permissions.includes('orders.dispatch');
  const { board, failed } = useCourierBoard(allowed);
  if (!user) return null;
  if (!allowed) return <div className="pr-card"><Empty icon="lock">{'Você não tem permissão para despachar pedidos.'}</Empty></div>;
  if (!board) return <div className="pr-card"><p className="vp-note" role="status">{failed ? 'Não foi possível carregar os entregadores.' : 'Carregando entregadores…'}</p></div>;
  const hasStore = board.restaurant.latitude != null && board.restaurant.longitude != null;
  const available = board.couriers.filter((courier) => courier.status === 'available').length;
  return <div className="vp-couriers">
    <div className="pr-card vp-map-card">
      {hasStore
        ? <CourierMap board={board} />
        // Só o suporte cadastra o endereço da loja (admin → Operação → Origem da entrega); a loja não tem esse campo.
        : <Empty icon="map-pin">{'O endereço da loja ainda não está cadastrado, então o mapa e a distância não aparecem. Peça ao suporte Foodie para cadastrar.'}</Empty>}
      {failed && <p className="vp-note" role="status">{'Sem conexão — mostrando a última atualização.'}</p>}
    </div>
    <Card className="pr-card--flush" title="Equipe" actions={board.couriers.length > 0 ? <span className={`pr-badge ${available ? 'is-brand' : ''}`}>{`${available} ${available === 1 ? 'disponível' : 'disponíveis'}`}</span> : undefined}>
      {board.couriers.length === 0
        ? <Empty icon="bike">{'Nenhum entregador cadastrado. Cadastre em Equipe e acessos.'}</Empty>
        : <ul className="pr-list">{board.couriers.map((courier) => <CourierRow courier={courier} key={courier.id} />)}</ul>}
    </Card>
  </div>;
}
