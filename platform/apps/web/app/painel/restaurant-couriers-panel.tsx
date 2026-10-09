'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { ratingSummary, stars, type Reputation } from '../entregas/reputation';
import { Badge, Button, Card, EmptyState, Field, TextInput } from '../ui';

const MIN_REVIEWS = 5;

/** Desempenho de um entregador: entregas em 30 dias e avaliações recentes (anônimas: só o número do pedido). */
function CourierPerformance({ courierId }: { courierId: number }) {
  const [data, setData] = useState<Reputation | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    api<Reputation>(`/restaurant/couriers/${courierId}/reputation`)
      .then((value) => { if (!cancelled) setData(value); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [courierId]);

  if (!data) return <small role="status">{failed ? 'Não foi possível carregar o desempenho.' : 'Carregando…'}</small>;
  const recent = data.recent ?? [];
  return (
    <div style={{ display: 'grid', gap: 6, width: '100%' }}>
      <small>{`Concluídas em 30 dias: ${data.completed30d} · Falhas: ${data.failed30d}`}</small>
      {recent.length === 0 ? <small>Nenhum comentário ainda.</small> : recent.map((review) => (
        <div key={`${review.orderId}-${review.createdAt}`} style={{ display: 'grid', gap: 2 }}>
          <strong role="img" aria-label={`${review.rating} de 5 estrelas`}>{stars(review.rating)}</strong>
          {review.comment && <small>{review.comment}</small>}
          <small>{`Pedido #${review.orderId}`}</small>
        </div>
      ))}
    </div>
  );
}

/**
 * Entregadores da loja. Decisão de 08/10/2026: o entregador é exclusivo de uma loja, que o cadastra,
 * aprova, suspende e despacha. Cadastrado aqui já sai aprovado.
 */
export default function RestaurantCouriersPanel() {
  const { couriers, busy, run } = useApp();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [openId, setOpenId] = useState<number | null>(null);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () => api('/restaurant/couriers', { method: 'POST', body: JSON.stringify({ name, email, password }) }),
      'Entregador cadastrado e liberado para entregas.',
    );
    if (ok) {
      setName('');
      setEmail('');
      setPassword('');
    }
  }

  return (
    <div className="team-panel">
      <Card title="Novo entregador" subtitle="O entregador atende só esta loja. Ele entra com o email e a senha cadastrados aqui.">
        <form className="form-grid" onSubmit={create}>
          <Field label="Nome">
            <TextInput value={name} onChange={(event) => setName(event.target.value)} placeholder="Nome completo" required />
          </Field>
          <Field label="Email">
            <TextInput type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="entregador@exemplo.com" required />
          </Field>
          <Field label="Senha inicial" hint="Mínimo de 12 caracteres">
            <TextInput type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} required />
          </Field>
          <Button type="submit" variant="secondary" disabled={busy}>
            Cadastrar entregador
          </Button>
        </form>
      </Card>

      <Card title="Entregadores">
        {couriers.length === 0 ? (
          <EmptyState title="Nenhum entregador cadastrado">Cadastre quem faz as entregas da loja para poder atribuir pedidos.</EmptyState>
        ) : (
          <ul className="team-list">
            {couriers.map((courier) => (
              <li key={courier.id}>
                <span>
                  <strong>{courier.name}</strong> · {courier.email}{' '}
                  {courier.suspended ? <Badge tone="danger">Suspenso</Badge> : courier.approved ? <Badge tone="success">Ativo</Badge> : <Badge tone="warning">Aguardando aprovação</Badge>}
                  <br />
                  <small>{ratingSummary(courier.ratingAverage, courier.ratingCount ?? 0, MIN_REVIEWS)}</small>
                </span>
                <span>
                  <Button variant="ghost" size="sm" style={{ minHeight: 44 }} aria-expanded={openId === courier.id} onClick={() => setOpenId(openId === courier.id ? null : courier.id)}>
                    {openId === courier.id ? 'Ocultar' : 'Ver desempenho'}
                  </Button>
                  {!courier.approved && !courier.suspended && (
                    <Button variant="secondary" size="sm" disabled={busy} onClick={() => void run(() => api(`/restaurant/couriers/${courier.id}/approval`, { method: 'PATCH' }), 'Entregador aprovado para entregas.')}>
                      Aprovar
                    </Button>
                  )}
                  <Button
                    variant={courier.suspended ? 'secondary' : 'danger'}
                    size="sm"
                    disabled={busy}
                    onClick={() => void run(() => api(`/restaurant/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessões encerradas.')}
                  >
                    {courier.suspended ? 'Reativar' : 'Suspender'}
                  </Button>
                </span>
                {openId === courier.id && <div style={{ flexBasis: '100%' }}><CourierPerformance courierId={courier.id} /></div>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
