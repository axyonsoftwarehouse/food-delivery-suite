'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Button, Card, Field, TextInput } from '../ui';

/** Telefone da loja: o entregador liga ou chama no WhatsApp durante a retirada. */
export default function StoreContactCard() {
  const { busy, run } = useApp();
  const [phone, setPhone] = useState('');
  const [requireCode, setRequireCode] = useState(false);
  useEffect(() => { api<{ phone: string | null; requireDeliveryCode: boolean }>('/restaurant/contact').then((data) => { setPhone(data.phone ?? ''); setRequireCode(data.requireDeliveryCode); }).catch(() => {}); }, []);
  return <Card title="Dados da loja" subtitle="O telefone aparece para os seus entregadores durante a entrega (Ligar e WhatsApp). Aqui você também liga o código de confirmação da entrega.">
    <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void run(() => api('/restaurant/contact', { method: 'PUT', body: JSON.stringify({ phone }) }), 'Telefone da loja salvo.'); }}>
      <Field label="Telefone da loja" hint="Com DDD. Ex.: (85) 3222-1100">
        <TextInput inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="(85) 3222-1100" />
      </Field>
      <Button type="submit" variant="secondary" disabled={busy}>Salvar</Button>
    </form>
    <label className="check">
      <input type="checkbox" checked={requireCode} disabled={busy}
        onChange={(event) => { const next = event.target.checked; void run(() => api('/restaurant/contact/delivery-code', { method: 'PUT', body: JSON.stringify({ required: next }) }).then(() => setRequireCode(next)), next ? 'Código de entrega ligado.' : 'Código de entrega desligado.'); }} />
      {' Exigir código de confirmação na entrega'}
      <span className="form-help">O cliente vê 4 dígitos no pedido e o entregador os digita ao entregar. Vale para os pedidos novos.</span>
    </label>
  </Card>;
}
