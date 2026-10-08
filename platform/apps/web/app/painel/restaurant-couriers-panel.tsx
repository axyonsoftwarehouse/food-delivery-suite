'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';
import { Badge, Button, Card, EmptyState, Field, TextInput } from '../ui';

/**
 * Entregadores da loja. Decisão de 08/10/2026: o entregador é exclusivo de uma loja, que o cadastra,
 * aprova, suspende e despacha. Cadastrado aqui já sai aprovado.
 */
export default function RestaurantCouriersPanel() {
  const { couriers, busy, run } = useApp();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

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
                </span>
                <span>
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
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
