'use client';

import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Chip, Chips, EmptyState, Field, IconButton, SelectInput, Spinner, StatusBadge, Tabs, TextArea, TextInput } from '../ui';

const SWATCHES: Array<{ name: string; token: string }> = [
  { name: 'Marca 500', token: '--brand-500' },
  { name: 'Marca 600', token: '--brand-600' },
  { name: 'Marca 700', token: '--brand-700' },
  { name: 'Marca 050', token: '--brand-050' },
  { name: 'Destaque', token: '--accent-500' },
  { name: 'Sucesso', token: '--success' },
  { name: 'Info', token: '--info' },
  { name: 'Aviso', token: '--warning' },
  { name: 'Erro', token: '--danger' },
  { name: 'Borda', token: '--border' },
  { name: 'Superfície', token: '--surface' },
  { name: 'Fundo', token: '--background' },
];

export default function GuiaUiPage() {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const [tab, setTab] = useState('todos');
  const [chip, setChip] = useState('all');

  useEffect(() => {
    if (theme === 'dark') document.documentElement.dataset.theme = 'dark';
    else delete document.documentElement.dataset.theme;
    return () => {
      delete document.documentElement.dataset.theme;
    };
  }, [theme]);

  return (
    <main style={{ width: 'min(980px, calc(100% - 40px))', margin: '0 auto', padding: '40px 0 80px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, marginBottom: 28 }}>
        <div>
          <span className="customer-kicker">DESIGN SYSTEM</span>
          <h1 style={{ font: '800 34px/1.1 Manrope, sans-serif', letterSpacing: '-1.5px', margin: '8px 0 0' }}>Guia de componentes</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, marginTop: 6 }}>Tokens e biblioteca de UI em <code>app/ui.tsx</code> + <code>app/tokens.css</code>.</p>
        </div>
        <Button variant="secondary" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? '☀️ Tema claro' : '🌙 Tema escuro'}
        </Button>
      </header>

      <div style={{ display: 'grid', gap: 22 }}>
        <Card title="Cores" subtitle="Variáveis em tokens.css">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 12 }}>
            {SWATCHES.map((swatch) => (
              <div key={swatch.token}>
                <div style={{ height: 54, borderRadius: 'var(--radius-md)', background: `var(${swatch.token})`, border: '1px solid var(--border)' }} />
                <strong style={{ display: 'block', fontSize: 12, marginTop: 6 }}>{swatch.name}</strong>
                <code style={{ fontSize: 11, color: 'var(--text-faint)' }}>{swatch.token}</code>
              </div>
            ))}
          </div>
        </Card>

        <Card title="Tipografia" subtitle="DM Sans no corpo, Manrope nos títulos">
          <h1 style={{ font: '800 var(--h1) var(--font-display)', margin: 0 }}>H1 Título</h1>
          <h2 style={{ font: '800 var(--h3) var(--font-display)', margin: '10px 0' }}>H3 Título</h2>
          <p style={{ fontSize: 'var(--text-lg)', margin: '6px 0' }}>Corpo grande para leitura confortável.</p>
          <p style={{ fontSize: 'var(--text-md)', color: 'var(--text-muted)', margin: 0 }}>Corpo médio e texto secundário.</p>
        </Card>

        <Card title="Botões">
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <Button>Primário</Button>
            <Button variant="secondary">Secundário</Button>
            <Button variant="ghost">Sutil</Button>
            <Button variant="danger">Perigo</Button>
            <Button size="sm">Pequeno</Button>
            <Button disabled>Desabilitado</Button>
            <IconButton label="Notificações">🔔</IconButton>
          </div>
        </Card>

        <Card title="Campos">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
            <Field label="Nome" hint="Como aparece para o cliente">
              <TextInput placeholder="Ex.: Ana Souza" />
            </Field>
            <Field label="Categoria">
              <SelectInput defaultValue="pizza">
                <option value="pizza">Pizza</option>
                <option value="salada">Salada</option>
              </SelectInput>
            </Field>
            <Field label="Email" error="Informe um email válido">
              <TextInput defaultValue="nao-e-email" />
            </Field>
          </div>
          <Field label="Observação">
            <TextArea placeholder="Sem cebola, por favor" />
          </Field>
        </Card>

        <Card title="Feedback e status">
          <div style={{ display: 'grid', gap: 10, marginBottom: 16 }}>
            <Alert tone="success">Pedido confirmado.</Alert>
            <Alert tone="info">Atualizado há 8 segundos.</Alert>
            <Alert tone="warning">Pedido aguardando aceite há 12 minutos.</Alert>
            <Alert tone="error">Não foi possível concluir. Tente novamente.</Alert>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <StatusBadge status="placed" />
            <StatusBadge status="accepted" />
            <StatusBadge status="ready" />
            <StatusBadge status="picked_up" />
            <StatusBadge status="delivered" />
            <StatusBadge status="cancelled" />
            <Badge tone="brand">PROMO</Badge>
            <Badge tone="neutral">NEW</Badge>
          </div>
        </Card>

        <Card title="Chips e abas">
          <Chips>
            {['all', 'hamburger', 'pizza', 'salada'].map((id) => (
              <Chip key={id} selected={chip === id} onClick={() => setChip(id)}>
                {id}
              </Chip>
            ))}
          </Chips>
          <div style={{ marginTop: 18 }}>
            <Tabs
              value={tab}
              onChange={setTab}
              tabs={[
                { id: 'todos', label: 'Todos' },
                { id: 'ativos', label: 'Ativos' },
                { id: 'concluidos', label: 'Concluídos' },
              ]}
            />
            <p style={{ marginTop: 12, color: 'var(--text-muted)', fontSize: 13 }}>Aba selecionada: {tab}</p>
          </div>
        </Card>

        <Card title="Estados vazios e carregamento">
          <div style={{ display: 'grid', gap: 16 }}>
            <EmptyState icon="🛒" title="Seu carrinho está vazio">Adicione pratos de um restaurante para começar.</EmptyState>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Spinner /> <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>Carregando pedidos…</span>
            </div>
          </div>
        </Card>
      </div>
    </main>
  );
}
