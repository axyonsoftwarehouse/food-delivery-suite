'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Button, Card, Field, TextInput } from '../ui';

/** Telefone da loja: o entregador liga ou chama no WhatsApp durante a retirada. */
export default function StoreContactCard() {
  const { busy, run } = useApp();
  const [phone, setPhone] = useState('');
  useEffect(() => { api<{ phone: string | null }>('/restaurant/contact').then((data) => setPhone(data.phone ?? '')).catch(() => {}); }, []);
  return <Card title="Dados da loja" subtitle="O telefone aparece para os seus entregadores durante a entrega (Ligar e WhatsApp).">
    <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void run(() => api('/restaurant/contact', { method: 'PUT', body: JSON.stringify({ phone }) }), 'Telefone da loja salvo.'); }}>
      <Field label="Telefone da loja" hint="Com DDD. Ex.: (85) 3222-1100">
        <TextInput inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="(85) 3222-1100" />
      </Field>
      <Button type="submit" variant="secondary" disabled={busy}>Salvar</Button>
    </form>
  </Card>;
}
