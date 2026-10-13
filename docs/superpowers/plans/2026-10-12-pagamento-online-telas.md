# Pagamento online claro — PR 2 (telas): plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Objetivo:** o cliente paga o pedido online numa página própria, com prazo, recusa em destaque e saídas
na mesma tela; a loja vê quem ainda não pagou e informa motivos num diálogo do app, sem `window.prompt`.

**Arquitetura:** a lógica que decide o que a tela mostra (prazo, estado da página, textos finais) fica num
módulo puro (`app/online-payment.ts`) testado com `node --test`, sem dependência nova. O diálogo de motivo
vira um hook genérico (`useReasonDialog`), e o `askReason` do `AppProvider` passa a ser assíncrono. A
página `/loja/pedidos/[id]/pagamento` lê `GET /orders/{id}` (que já traz `payment` com `pay_deadline` e
`rejection_message`, da PR 1) e cobra por `POST /orders/{id}/payment/online`.

**Tecnologia:** Next 16.3.6 (App Router, componentes `'use client'`), React 19.3, TypeScript 7, Node 24
(`node --test` roda `.ts` direto, removendo os tipos).

**Spec:** `docs/superpowers/specs/2026-10-11-pagamento-online-clareza-design.md` (regras 10 a 15). A PR 1
(`feat/pagamento-online-clareza`, PR #31) entrega os campos que esta PR consome.

## Restrições gerais

- Branch `feat/pagamento-online-telas`, criada a partir de `feat/pagamento-online-clareza`. Abrir o PR
  contra a `main` só depois da PR 1 mesclada; até lá, PR em rascunho com base na branch da PR 1.
- Textos da tela exatamente como na spec: "Aguardando pagamento", "Pague até HH:MM (faltam N min)",
  "O cartão foi recusado. Nenhum valor foi cobrado.", "O Pix gerado será cancelado.", "Cancelar pedido",
  "Pagamento aprovado! A <loja> já recebeu seu pedido.", "Acompanhar pedido", "O prazo para pagar acabou
  e o pedido foi cancelado. Nada foi cobrado.", "Voltar à loja", "Libera quando o cliente pagar", "Pagar".
- Prazo de 15 min (vem pronto do servidor); destaque nos **últimos 3 min**; consulta do status a cada
  **5 s** enquanto o cliente paga.
- Limites de motivo iguais aos da API: loja **3 a 255** (`OrderService`: "Informe o motivo (3 a 255
  caracteres)"); suporte em cancelar/estornar **10 a 255** (`normalizeReason` exige 10, `@Size(max = 255)`
  no corpo); intervenção do suporte fora de pedido **10 a 500**.
- Nenhuma dependência nova. Nenhuma tela tocada por esta PR usa `window.prompt` nos fluxos da spec
  (Recusar, Cancelar, Estornar, intervenção do suporte, cancelamento pelo cliente). `receivePayment`
  ("Valor recebido em dinheiro") fica para a PR seguinte (fora do escopo da spec).
- Estornar aparece em pedido **pago** com status `delivered`, `completed` ou `failed` — a regra que a PR 1
  implementou (commit `341b80c`), mais larga que a regra 15 da spec ("só entregue").
- Repositório público: nada de segredo, nem credencial de teste, no diff.

## Foco da revisão

1. **Fuso das datas.** A API manda `pay_deadline`/`accept_deadline` sem fuso (ex.: `2026-10-12T14:35:00`)
   e a tela lê com `new Date(...)`, como já faz com `created_at` no painel. Se o servidor gravar em UTC e o
   navegador estiver em America/Fortaleza, o prazo erra 3 h. **(a confirmar no staging)** — a Tarefa 7
   confere "Pague até" contra o horário do pedido + 15 min.
2. **Prazo zerado antes de o servidor expirar.** A contagem chega a zero, mas a expiração roda num
   agendamento; a página não pode oferecer pagamento nem parecer travada. Estado `closing` ("Conferindo o
   pedido…"), sem abas nem botões, consultando a cada 5 s — testado na Tarefa 1.
3. **Endereço inválido ou pedido de outro cliente** (`/loja/pedidos/abc/pagamento`, id de outra pessoa):
   "Pedido não encontrado" com link para Pedidos, nunca tela em branco — `parseOrderId` testado na Tarefa 1,
   404/403 tratados na Tarefa 5.
4. **Pedido que não é para pagar ali** (já pago ao abrir, na entrega, Manual): volta para Pedidos — estado
   `leave` testado na Tarefa 1.
5. **Falha da cobrança** (409 da troca Pix→cartão, queda de rede): a mensagem do servidor aparece num
   aviso, o formulário do cartão volta para nova tentativa e nunca aparece "HTTP 402" — conferido no roteiro
   da Tarefa 7.

---

### Tarefa 1: Regras puras da tela de pagamento (prazo e estado) com testes

**Arquivos:**
- Criar: `platform/apps/web/app/online-payment.ts`
- Criar: `platform/apps/web/app/online-payment.test.ts`
- Criar: `platform/apps/web/app/use-now.ts`
- Alterar: `platform/apps/web/tsconfig.json` (excluir `**/*.test.ts`)
- Alterar: `platform/scripts/verify.mjs:52` (novo passo depois de "Tipos do site")

**Interfaces:**
- Produz (`online-payment.ts`):
  - `type DeadlineInfo = { at: string; minutesLeft: number; urgent: boolean; over: boolean }`
  - `deadlineInfo(deadline: string | null | undefined, now: number): DeadlineInfo | null`
  - `deadlineText(prefix: string, info: DeadlineInfo): string`
  - `awaitingOnlinePayment(order: { status: string; payment_modality?: string | null; payment_status: string | null }): boolean`
  - `type PaymentSnapshot = { method: string; modality: string | null; status: string; image?: { qr_code?: string | null } | null }`
  - `hasLivePix(payment: PaymentSnapshot | null | undefined): boolean`
  - `type PageState = 'pay' | 'closing' | 'approved' | 'expired' | 'cancelled' | 'rejected_by_store' | 'leave'`
  - `paymentPageState(input: { orderStatus: string; payment: PaymentSnapshot | null; payDeadlineOver: boolean; paidHere: boolean }): PageState`
  - `finalMessage(state: PageState, paymentStatus: string | null, restaurant: string): { text: string; button: string; href: string } | null`
  - `parseOrderId(param: string | string[] | undefined): number | null`
- Produz (`use-now.ts`): `useNow(intervalMs: number): number`

- [ ] **Passo 1: escrever os testes que falham**

`platform/apps/web/app/online-payment.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  awaitingOnlinePayment, deadlineInfo, deadlineText, finalMessage, hasLivePix, parseOrderId, paymentPageState,
} from './online-payment.ts';

// Datas sem fuso, como a API manda: as duas pontas são lidas no fuso local, então o teste não depende de TZ.
const at = (value: string) => new Date(value).getTime();

test('prazo: hora local e minutos que faltam, arredondando para cima', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:27:30'));
  assert.deepEqual(info, { at: '14:35', minutesLeft: 8, urgent: false, over: false });
  assert.equal(deadlineText('Pague até', info!), 'Pague até 14:35 (faltam 8 min)');
});

test('prazo: últimos 3 minutos em destaque e 1 minuto no singular', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:34:10'))!;
  assert.equal(info.urgent, true);
  assert.equal(deadlineText('Pague até', info), 'Pague até 14:35 (falta 1 min)');
  assert.equal(deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:31:59'))!.urgent, false);
});

test('prazo vencido: encerrado, sem destaque e sem minutos', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:35:00'))!;
  assert.deepEqual({ over: info.over, urgent: info.urgent, minutesLeft: info.minutesLeft }, { over: true, urgent: false, minutesLeft: 0 });
  assert.equal(deadlineText('Pague até', info), 'Pague até 14:35 (prazo encerrado)');
});

test('prazo ausente ou ilegível não quebra a tela', () => {
  assert.equal(deadlineInfo(null, Date.now()), null);
  assert.equal(deadlineInfo(undefined, Date.now()), null);
  assert.equal(deadlineInfo('não é data', Date.now()), null);
});

test('aguardando pagamento: só online, novo e não pago', () => {
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'pending' }), true);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'rejected' }), true);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'paid' }), false);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'on_delivery', payment_status: 'pending' }), false);
  assert.equal(awaitingOnlinePayment({ status: 'accepted', payment_modality: 'online', payment_status: 'pending' }), false);
});

const pix = { method: 'pix', modality: 'online', status: 'pending', image: { qr_code: '000201...' } };

test('Pix vivo: Pix pendente com código', () => {
  assert.equal(hasLivePix(pix), true);
  assert.equal(hasLivePix({ ...pix, status: 'cancelled' }), false);
  assert.equal(hasLivePix({ ...pix, image: { qr_code: null } }), false);
  assert.equal(hasLivePix({ ...pix, method: 'card' }), false);
  assert.equal(hasLivePix(null), false);
});

test('estado da página', () => {
  const base = { orderStatus: 'placed', payment: pix, payDeadlineOver: false, paidHere: false };
  assert.equal(paymentPageState(base), 'pay');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, method: 'card', status: 'rejected' } }), 'pay');
  assert.equal(paymentPageState({ ...base, payDeadlineOver: true }), 'closing');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, status: 'paid' }, paidHere: true }), 'approved');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, status: 'paid' } }), 'leave');
  assert.equal(paymentPageState({ ...base, payment: null }), 'leave');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, modality: 'on_delivery' } }), 'leave');
  assert.equal(paymentPageState({ ...base, orderStatus: 'expired', payment: { ...pix, status: 'cancelled' } }), 'expired');
  assert.equal(paymentPageState({ ...base, orderStatus: 'cancelled' }), 'cancelled');
  assert.equal(paymentPageState({ ...base, orderStatus: 'rejected' }), 'rejected_by_store');
  assert.equal(paymentPageState({ ...base, orderStatus: 'accepted' }), 'leave');
});

test('mensagens finais: nada cobrado ou estornado, conforme o pagamento', () => {
  assert.deepEqual(finalMessage('approved', 'paid', 'Pizzaria do Zé'), { text: 'Pagamento aprovado! A Pizzaria do Zé já recebeu seu pedido.', button: 'Acompanhar pedido', href: '/loja/pedidos' });
  assert.deepEqual(finalMessage('expired', 'cancelled', 'X'), { text: 'O prazo para pagar acabou e o pedido foi cancelado. Nada foi cobrado.', button: 'Voltar à loja', href: '/loja' });
  assert.equal(finalMessage('expired', 'refunded', 'X')!.text, 'O pedido expirou sem o aceite da loja. O valor pago foi estornado.');
  assert.equal(finalMessage('cancelled', 'pending', 'X')!.text, 'Você cancelou o pedido. Nada foi cobrado.');
  assert.equal(finalMessage('cancelled', 'refunded', 'X')!.text, 'Pedido cancelado. O valor pago foi estornado.');
  assert.equal(finalMessage('rejected_by_store', 'cancelled', 'X')!.text, 'A loja recusou o pedido. Nada foi cobrado.');
  assert.equal(finalMessage('pay', 'pending', 'X'), null);
});

test('id da rota: só inteiro positivo', () => {
  assert.equal(parseOrderId('52'), 52);
  assert.equal(parseOrderId('abc'), null);
  assert.equal(parseOrderId('0'), null);
  assert.equal(parseOrderId('5.5'), null);
  assert.equal(parseOrderId(['52']), null);
  assert.equal(parseOrderId(undefined), null);
});
```

- [ ] **Passo 2: rodar e ver falhar**

Rodar (em `platform/apps/web`): `node --test app/online-payment.test.ts`
Esperado: FALHA com `Cannot find module '.../app/online-payment.ts'`.

- [ ] **Passo 3: implementar o módulo**

`platform/apps/web/app/online-payment.ts`:

```ts
/**
 * O que a tela mostra de um pagamento online (spec de 11/10/2026, regras 10 a 15). Puro, sem React:
 * os testes rodam com `node --test`. As datas chegam da API sem fuso e são lidas no fuso do navegador,
 * como o resto do painel.
 */

export type DeadlineInfo = { at: string; minutesLeft: number; urgent: boolean; over: boolean };

/** Destaque nos últimos 3 minutos do prazo. */
const URGENT_MS = 3 * 60_000;

export function deadlineInfo(deadline: string | null | undefined, now: number): DeadlineInfo | null {
  if (!deadline) return null;
  const end = new Date(deadline).getTime();
  if (Number.isNaN(end)) return null;
  const left = end - now;
  return {
    at: new Date(end).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    minutesLeft: Math.max(0, Math.ceil(left / 60_000)),
    urgent: left > 0 && left <= URGENT_MS,
    over: left <= 0,
  };
}

export function deadlineText(prefix: string, info: DeadlineInfo) {
  if (info.over) return `${prefix} ${info.at} (prazo encerrado)`;
  return `${prefix} ${info.at} (${info.minutesLeft === 1 ? 'falta 1 min' : `faltam ${info.minutesLeft} min`})`;
}

/** Pedido "Pagar agora" que a loja ainda não pode aceitar: o cliente não pagou. */
export function awaitingOnlinePayment(order: { status: string; payment_modality?: string | null; payment_status: string | null }) {
  return order.status === 'placed' && order.payment_modality === 'online' && order.payment_status !== 'paid';
}

export type PaymentSnapshot = { method: string; modality: string | null; status: string; image?: { qr_code?: string | null } | null };

/** Há um Pix gerado e ainda pagável (a troca para cartão o cancela no provedor, regra 5). */
export function hasLivePix(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'pix' && payment.status === 'pending' && !!payment.image?.qr_code;
}

export type PageState = 'pay' | 'closing' | 'approved' | 'expired' | 'cancelled' | 'rejected_by_store' | 'leave';

/**
 * `paidHere`: o pagamento foi aprovado com a página aberta (mostra o aviso verde). Pedido que já estava
 * pago ao abrir, ou que não é online, volta para Pedidos (`leave`).
 */
export function paymentPageState(input: { orderStatus: string; payment: PaymentSnapshot | null; payDeadlineOver: boolean; paidHere: boolean }): PageState {
  const { orderStatus, payment, payDeadlineOver, paidHere } = input;
  if (!payment || payment.modality !== 'online') return 'leave';
  if (payment.status === 'paid') return paidHere ? 'approved' : 'leave';
  if (orderStatus === 'expired') return 'expired';
  if (orderStatus === 'cancelled') return 'cancelled';
  if (orderStatus === 'rejected') return 'rejected_by_store';
  if (orderStatus !== 'placed') return 'leave';
  return payDeadlineOver ? 'closing' : 'pay';
}

export function finalMessage(state: PageState, paymentStatus: string | null, restaurant: string) {
  const refunded = paymentStatus === 'refunded';
  const back = { button: 'Voltar à loja', href: '/loja' };
  switch (state) {
    case 'approved':
      return { text: `Pagamento aprovado! A ${restaurant} já recebeu seu pedido.`, button: 'Acompanhar pedido', href: '/loja/pedidos' };
    case 'expired':
      return { text: refunded ? 'O pedido expirou sem o aceite da loja. O valor pago foi estornado.' : 'O prazo para pagar acabou e o pedido foi cancelado. Nada foi cobrado.', ...back };
    case 'cancelled':
      return { text: refunded ? 'Pedido cancelado. O valor pago foi estornado.' : 'Você cancelou o pedido. Nada foi cobrado.', ...back };
    case 'rejected_by_store':
      return { text: refunded ? 'A loja recusou o pedido. O valor pago foi estornado.' : 'A loja recusou o pedido. Nada foi cobrado.', ...back };
    default:
      return null;
  }
}

export function parseOrderId(param: string | string[] | undefined) {
  if (typeof param !== 'string' || !/^[1-9]\d*$/.test(param)) return null;
  return Number(param);
}
```

`platform/apps/web/app/use-now.ts`:

```ts
'use client';

import { useEffect, useState } from 'react';

/** Relógio para contagens regressivas: re-renderiza a cada `intervalMs`. */
export function useNow(intervalMs: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}
```

- [ ] **Passo 4: rodar e ver passar**

Rodar: `node --test app/online-payment.test.ts`
Esperado: `# pass 9`, `# fail 0`.

- [ ] **Passo 5: tirar o teste do `tsc` e pôr no `verify`**

O teste importa `./online-payment.ts` com extensão (exigência do `node --test`), o que o `tsc` do site
recusa (TS5097). Em `platform/apps/web/tsconfig.json`:

```json
  "exclude": [
    "node_modules",
    "**/*.test.ts"
  ]
```

Em `platform/scripts/verify.mjs`, logo depois da linha `run('Tipos do site (tsc)', ...)`:

```js
run('Testes do site (node --test)', 'node', ['--test', 'app/online-payment.test.ts'], { cwd: join(root, 'apps/web') });
```

Rodar (em `platform/apps/web`): `npx tsc --noEmit`
Esperado: sem saída (código 0).

- [ ] **Passo 6: commit**

```bash
git add platform/apps/web/app/online-payment.ts platform/apps/web/app/online-payment.test.ts platform/apps/web/app/use-now.ts platform/apps/web/tsconfig.json platform/scripts/verify.mjs
git commit -m "feat(web): regras puras do prazo e do estado do pagamento online, com testes"
```

---

### Tarefa 2: Diálogo de motivo genérico

**Arquivos:**
- Criar: `platform/apps/web/app/ReasonDialog.tsx`
- Alterar: `platform/apps/web/app/SupportReasonDialog.tsx` (passa a usar o genérico; mesma interface)

**Interfaces:**
- Produz: `type ReasonOptions = { title: string; action?: string; warning?: string; label?: string; min: number; max: number; confirmLabel?: string; cancelLabel?: string; draftKey?: string }`
- Produz: `useReasonDialog(): { ask: (options: ReasonOptions) => Promise<string | null>; dialog: ReactElement | null }` — `ask` é estável (mesma referência entre renders).
- Mantém: `useSupportReason(): { askReason: AskReason; dialog: ReactElement | null }` (usado por `CatalogManager`, `RestaurantHours`, `support-profile`) com o mesmo texto de hoje.

- [ ] **Passo 1: criar `ReasonDialog.tsx`**

Conferir antes: `ui.tsx` não importa `app-context` (só `react`), então o `AppProvider` pode importar este
arquivo sem ciclo.

```tsx
'use client';

import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent, type ReactElement } from 'react';
import { Alert, Button, Field, TextArea } from './ui';

/**
 * Pedido de motivo em diálogo do app. Substitui o `window.prompt`, que o navegador do app não mostra
 * (roteiro de 11/10/2026: Recusar, Cancelar e Estornar não faziam nada).
 */
export type ReasonOptions = {
  title: string;
  /** Linha "Ação: …" abaixo do título (intervenção do suporte). */
  action?: string;
  /** Aviso em destaque acima do campo. */
  warning?: string;
  label?: string;
  min: number;
  max: number;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Mesma chave: o rascunho de um pedido desistido volta ao abrir de novo. */
  draftKey?: string;
};

const FOCUSABLE = 'button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

export function useReasonDialog(): { ask: (options: ReasonOptions) => Promise<string | null>; dialog: ReactElement | null } {
  const [options, setOptions] = useState<ReasonOptions | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: string | null) => void) | null>(null);
  const draft = useRef<{ key: string; text: string } | null>(null);
  const box = useRef<HTMLFormElement | null>(null);
  const opener = useRef<HTMLElement | null>(null);
  const titleId = useId();

  const ask = useCallback((next: ReasonOptions) => new Promise<string | null>((resolve) => {
    // Um pedido novo com outro ainda aberto encerra o anterior como desistência.
    resolver.current?.(null);
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setText(next.draftKey && draft.current?.key === next.draftKey ? draft.current.text : '');
    setOptions(next);
  }), []);

  const close = useCallback((value: string | null) => {
    if (options?.draftKey) draft.current = value === null && text.trim() ? { key: options.draftKey, text } : null;
    resolver.current?.(value);
    resolver.current = null;
    setOptions(null);
    opener.current?.focus();
    opener.current = null;
  }, [options, text]);

  useEffect(() => {
    if (!options) return;
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(null); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [options, close]);

  function trapFocus(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key !== 'Tab' || !box.current) return;
    const items = Array.from(box.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  const length = text.trim().length;
  const valid = options !== null && length >= options.min && length <= options.max;
  const dialog = options === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <form ref={box} className="support-dialog__box" onKeyDown={trapFocus} onSubmit={(event) => { event.preventDefault(); if (valid) close(text.trim()); }}>
        <h3 id={titleId}>{options.title}</h3>
        {options.action && <p className="support-dialog__action"><span>{'Ação:'}</span> <strong>{options.action}</strong></p>}
        {options.warning && <Alert tone="warning">{options.warning}</Alert>}
        <Field label={options.label ?? `Motivo (${options.min} a ${options.max} caracteres)`} hint={`${length}/${options.max}`}>
          <TextArea autoFocus required minLength={options.min} maxLength={options.max} rows={4} value={text} onChange={(event) => setText(event.target.value)} />
        </Field>
        <div className="support-dialog__actions">
          <Button type="button" variant="ghost" onClick={() => close(null)}>{options.cancelLabel ?? 'Voltar'}</Button>
          <Button type="submit" disabled={!valid}>{options.confirmLabel ?? 'Confirmar'}</Button>
        </div>
      </form>
    </div>
  );

  return { ask, dialog };
}
```

- [ ] **Passo 2: reescrever `SupportReasonDialog.tsx` sobre o genérico**

```tsx
'use client';

import { useCallback, type ReactElement } from 'react';
import { useReasonDialog } from './ReasonDialog';
import type { AskReason } from './support-request';

const REASON_ACTION_LABELS: Record<string, string> = { post: 'Criar registro', put: 'Alterar registro', patch: 'Alterar registro', delete: 'Excluir registro' };

/** Motivo das escritas do modo suporte (E48): mesmo diálogo do painel, com o aviso de que a loja é avisada. */
export function useSupportReason(): { askReason: AskReason; dialog: ReactElement | null } {
  const { ask, dialog } = useReasonDialog();
  const askReason = useCallback<AskReason>((next) => ask({
    title: 'Motivo da intervenção',
    action: next.action ?? REASON_ACTION_LABELS[next.method.toLowerCase()] ?? 'Alterar registro',
    warning: 'Esta alteração será feita em nome da loja, registrada na trilha e avisada à loja.',
    min: 10,
    max: 500,
    label: 'Motivo (10 a 500 caracteres)',
    confirmLabel: 'Confirmar intervenção',
    cancelLabel: 'Cancelar',
    draftKey: `${next.method} ${next.action ?? ''}`,
  }), [ask]);
  return { askReason, dialog };
}
```

- [ ] **Passo 3: tipos**

Rodar (em `platform/apps/web`): `npx tsc --noEmit`
Esperado: sem saída.

- [ ] **Passo 4: commit**

```bash
git add platform/apps/web/app/ReasonDialog.tsx platform/apps/web/app/SupportReasonDialog.tsx
git commit -m "refactor(web): diálogo de motivo genérico, base do modo suporte"
```

---

### Tarefa 3: `askReason` assíncrono no painel e no cliente (sem `window.prompt`)

**Arquivos:**
- Alterar: `platform/apps/web/app/app-context.tsx:122` (tipo), `:277-281` (`askReason`), `:296-303` (`refundPayment`), `:386` (renderizar o diálogo)
- Alterar: `platform/apps/web/app/painel/orders-panel.tsx:60` (`reason`)
- Alterar: `platform/apps/web/app/painel/admin-operation-panel.tsx:9-14` (`linkToStore`)
- Alterar: `platform/apps/web/app/loja/customer-context.tsx:128` (tipo) e `:539-548` (`cancelOrder`)
- Alterar: `platform/apps/web/app/loja/pedidos/page.tsx:43` (`RowProps`)

**Interfaces:**
- Consome: `useReasonDialog` (Tarefa 2).
- Produz: `askReason: (title: string, limits?: { min?: number; max?: number; confirmLabel?: string }) => Promise<string | null>` no `AppValue` (padrão 3 a 255).
- Produz: `cancelOrder: (orderId: number) => Promise<boolean>` no `CustomerValue` (`true` se cancelou).

- [ ] **Passo 1: `app-context.tsx`**

Importar: `import { useReasonDialog } from './ReasonDialog';`

Tipo no `AppValue`:

```ts
  /** Motivo em diálogo do app (o `window.prompt` não aparece no navegador do app). Padrão: 3 a 255 caracteres. */
  askReason: (title: string, limits?: { min?: number; max?: number; confirmLabel?: string }) => Promise<string | null>;
```

Dentro de `AppProvider`, no lugar da função `askReason` atual:

```ts
  const reasonDialog = useReasonDialog();
  function askReason(title: string, limits: { min?: number; max?: number; confirmLabel?: string } = {}) {
    return reasonDialog.ask({ title, min: limits.min ?? 3, max: limits.max ?? 255, confirmLabel: limits.confirmLabel });
  }
```

`refundPayment`:

```ts
  async function refundPayment(order: Order) {
    // Admin estorna só como suporte, em nome da loja: o motivo vai para a trilha da loja (10+ caracteres).
    const support = user?.role === 'admin';
    const reason = await askReason(support ? 'Motivo do estorno (suporte, em nome da loja)' : 'Motivo do estorno', { min: support ? 10 : 3, max: 255, confirmLabel: 'Estornar' });
    if (!reason) return;
    await run(() => api(`/orders/${order.id}/payment/refund`, { method: 'POST', body: JSON.stringify({ note: reason }) }), 'Pagamento estornado.');
  }
```

No `return`: `return <AppContext.Provider value={value}>{children}{reasonDialog.dialog}</AppContext.Provider>;`

- [ ] **Passo 2: `orders-panel.tsx` linha 60**

```ts
  // Admin cancela como suporte: o motivo vai para a trilha da loja e exige 10 caracteres.
  const reason = async (title: string, action: string, ok: string, order: Order) => {
    const value = await askReason(title, role === 'admin' ? { min: 10 } : undefined);
    if (value) await run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, reason: value }) }), ok);
  };
```

Tirar os dois-pontos finais dos títulos (agora são títulos de diálogo): `'Motivo da recusa'`,
`'Motivo da falha na entrega'`, `'Motivo do cancelamento (o cliente é avisado e o pagamento online é estornado)'`,
`'Motivo do cancelamento'`. Os `onClick` continuam `() => reason(...)` (a promessa é descartada de propósito; o `run` mostra o erro).

- [ ] **Passo 3: `admin-operation-panel.tsx`**

```ts
  const linkToStore = async (courierId: number) => {
    const restaurantId = Number(linkTarget[courierId] || 0);
    if (!restaurantId) return;
    const reason = await askReason('Motivo da intervenção', { min: 10, max: 500 });
    if (reason) await run(() => api(`/admin/couriers/${courierId}/restaurant`, { method: 'PATCH', body: JSON.stringify({ restaurantId, reason }) }), 'Entregador ligado à loja.');
  };
```

O botão: `onClick={() => void linkToStore(courier.id)}`.

- [ ] **Passo 4: `customer-context.tsx` (`cancelOrder`)**

Tipo: `cancelOrder: (orderId: number) => Promise<boolean>;`

```ts
  async function cancelOrder(orderId: number) {
    const reason = await app.askReason('Motivo do cancelamento', { confirmLabel: 'Cancelar pedido' });
    if (!reason) return false;
    // A API diz se houve estorno automático (pagamento online já pago); só então prometemos o dinheiro de volta.
    let refunded = false;
    return app.run(async () => {
      const result = await request<{ paymentRefunded?: boolean }>(`/orders/${orderId}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason }) });
      refunded = result?.paymentRefunded === true;
    }, () => (refunded ? 'Pedido cancelado. O pagamento foi estornado.' : 'Pedido cancelado.'));
  }
```

Em `loja/pedidos/page.tsx`, `RowProps.cancelOrder: (orderId: number) => Promise<boolean>`.

- [ ] **Passo 5: conferir que não sobrou `window.prompt` nesses fluxos**

Rodar (em `platform/apps/web`): `grep -n "window.prompt" app/app-context.tsx app/painel/orders-panel.tsx app/painel/admin-operation-panel.tsx app/loja/customer-context.tsx`
Esperado: só a linha de `receivePayment` em `app-context.tsx` (fora do escopo).

Rodar: `npx tsc --noEmit` — esperado: sem saída.

- [ ] **Passo 6: conferir no navegador (dev local)**

Com a API e o site locais no ar (`.claude/launch.json`), no painel da loja: Recusar um pedido novo abre o
diálogo; Esc fecha e devolve o foco ao botão; "Confirmar" fica desabilitado abaixo de 3 caracteres;
confirmar recusa o pedido. No cliente, "Cancelar pedido" em Pedidos abre o mesmo diálogo. Print de cada um.

- [ ] **Passo 7: commit**

```bash
git add platform/apps/web/app/app-context.tsx platform/apps/web/app/painel/orders-panel.tsx platform/apps/web/app/painel/admin-operation-panel.tsx platform/apps/web/app/loja/customer-context.tsx platform/apps/web/app/loja/pedidos/page.tsx
git commit -m "feat(web): motivo em diálogo do app no painel e no cancelamento do cliente"
```

---

### Tarefa 4: Cartão do pedido no painel da loja (regra 15)

**Arquivos:**
- Alterar: `platform/apps/web/app/app-context.tsx:15` (tipo `Order`)
- Alterar: `platform/apps/web/app/painel/orders-panel.tsx` (`actions`, `card`)
- Alterar: `platform/apps/web/app/painel.css` (perto de `.op-pay`, linha ~209)

**Interfaces:**
- Consome: `awaitingOnlinePayment`, `deadlineInfo`, `deadlineText` (Tarefa 1), `useNow` (Tarefa 1).
- Produz: `Order` com `pay_deadline?: string | null; accept_deadline?: string | null; payment_raw_status?: string | null; payment_rejection_message?: string | null` (campos da listagem da PR 1).

- [ ] **Passo 1: tipo `Order`**

No tipo `Order` (uma linha só, campos separados por `;`), logo depois de `has_delivery_code?: number | boolean`:

```ts
; pay_deadline?: string | null; accept_deadline?: string | null; payment_raw_status?: string | null; payment_rejection_message?: string | null
```

(O `}` que fecha o tipo continua no fim.)

- [ ] **Passo 2: ações**

Importar em `orders-panel.tsx`:

```ts
import { awaitingOnlinePayment, deadlineInfo, deadlineText } from '../online-payment';
import { useNow } from '../use-now';
import { Badge } from '../ui';
```

No início do componente: `const now = useNow(30_000);`

Aceitar/Recusar (linha 71) vira:

```tsx
      {role === 'restaurant' && order.status === 'placed' && <>
        <button disabled={busy || awaitingOnlinePayment(order)} onClick={() => act(order, 'accept', 'Pedido aceito.')}><Icon name="check" size={16} />Aceitar</button>
        <button className="availability-button" disabled={busy} onClick={() => reason('Motivo da recusa', 'reject', 'Pedido recusado.', order)}>Recusar</button>
        {awaitingOnlinePayment(order) && <small className="form-help">{'Libera quando o cliente pagar'}</small>}
      </>}
```

Estornar (linha 85) passa a exigir pedido encerrado sem volta, a regra da PR 1:

```tsx
      {order.payment_status === 'paid' && ['delivered', 'completed', 'failed'].includes(order.status) && ((role === 'admin' && permissions.includes('support.act')) || (role === 'restaurant' && permissions.includes('payments.manage'))) && <button className="availability-button" disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
```

- [ ] **Passo 3: selo e prazos no cartão**

Em `card(order, index)`, depois de `const who = ...`:

```ts
    const awaiting = awaitingOnlinePayment(order);
    const pay = awaiting ? deadlineInfo(order.pay_deadline, now) : null;
    const accept = order.status === 'placed' && !awaiting && order.payment_modality === 'online' ? deadlineInfo(order.accept_deadline, now) : null;
```

Logo depois da linha `<div className="op-money">…</div>`:

```tsx
      {awaiting && <div className="op-await"><Badge tone="warning">{'Aguardando pagamento'}</Badge>{pay && <small className={`op-deadline${pay.urgent ? ' is-urgent' : ''}`}><Icon name="clock" size={14} />{deadlineText('Pague até', pay)}</small>}</div>}
      {accept && <div className="op-await"><Badge tone="success">{'Pago'}</Badge><small className={`op-deadline${accept.urgent ? ' is-urgent' : ''}`}><Icon name="clock" size={14} />{deadlineText('Aceite até', accept)}</small></div>}
```

- [ ] **Passo 4: CSS em `painel.css`, depois das regras de `.op-pay`**

```css
.op-await { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.op-deadline { display: inline-flex; align-items: center; gap: 4px; color: var(--text-muted); font-size: var(--text-xs); }
.op-deadline.is-urgent { color: #b42318; font-weight: var(--weight-bold); }
[data-theme='dark'] .op-deadline.is-urgent { color: #fda29b; }
```

- [ ] **Passo 5: tipos e tela**

Rodar: `npx tsc --noEmit` — esperado: sem saída.
No dev local, com um pedido "Pagar agora" ainda não pago: em "Novos", selo "Aguardando pagamento",
"Pague até HH:MM (faltam N min)", "Aceitar" desabilitado e "Libera quando o cliente pagar". Print.

- [ ] **Passo 6: commit**

```bash
git add platform/apps/web/app/app-context.tsx platform/apps/web/app/painel/orders-panel.tsx platform/apps/web/app/painel.css
git commit -m "feat(web): painel mostra pedido aguardando pagamento, prazos e estorno só após encerrar"
```

---

### Tarefa 5: Página de pagamento do cliente (regras 10 e 11)

**Arquivos:**
- Criar: `platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx`
- Alterar: `platform/apps/web/app/CardPaymentForm.tsx` (`onSubmit` devolve o resultado; a recusa é mostrada pela página)
- Alterar: `platform/apps/web/app/loja.css` (fim do arquivo)

**Interfaces:**
- Consome: `deadlineInfo`, `deadlineText`, `hasLivePix`, `paymentPageState`, `finalMessage`, `parseOrderId` (Tarefa 1); `useNow` (Tarefa 1); `useCustomer().cancelOrder` → `Promise<boolean>` (Tarefa 3); `api`, `ApiError`, `money`, `useApp` de `app-context`.
- Produz: `CardPaymentForm` com `onSubmit: (data: CardChargeData) => Promise<'paid' | 'rejected' | 'pending'>`.
- API (PR 1): `GET /orders/{id}` → `{ id, status, restaurant_id, restaurant_name, total_cents, payment: { method, modality, status, raw_status, pay_deadline, rejection_message, image: { qr_code, qr_code_base64, ticket_url } } | null }`; `POST /orders/{id}/payment/online` com `{ method: 'pix' }` ou `{ method: 'card', cardToken, paymentMethodId, installments, docType, docNumber }` → detalhe do pagamento com `status`.

- [ ] **Passo 1: `CardPaymentForm` devolve o resultado em vez de mostrar a mensagem**

Na assinatura:

```ts
  onSubmit: (data: CardChargeData) => Promise<'paid' | 'rejected' | 'pending'>;
```

O callback `onSubmit` do Brick vira:

```ts
            onSubmit: async (dados: CardData) => {
              const resultado = await enviar.current({
                token: dados.token,
                paymentMethodId: dados.payment_method_id,
                installments: dados.installments,
                docType: dados.payer?.identification?.type,
                docNumber: dados.payer?.identification?.number,
              });
              // Recusado: rejeitar devolve o formulário para o cliente tentar outro cartão. O motivo é
              // mostrado pela página, em destaque e acima do formulário (spec de 11/10, regra 10).
              if (resultado === 'rejected') throw new Error('Pagamento recusado.');
            },
```

O `mensagem` passa a ser só de erro de carregamento: trocar
`{mensagem && <p role="status" className={estado === 'erro' ? 'customer-minimum' : 'form-help'}>{mensagem}</p>}`
por `{estado === 'erro' && mensagem && <p role="alert" className="customer-minimum">{mensagem}</p>}`.
Trocar `'Carregando o formulário seguro do Mercado Pago...'` por `'Carregando…'` (some no `onReady`, que já
muda `estado` para `pronto`). Tirar o título "PAGAMENTO COM CARTÃO / Informe os dados do cartão" (as abas
da página já dizem isso): remover a linha `<div className="customer-card-title">…</div>`.

- [ ] **Passo 2: a página**

`platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx`:

```tsx
'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { ApiError, api, money, useApp } from '../../../../app-context';
import { useCustomer, type CardChargeData } from '../../../customer-context';
import CardPaymentForm from '../../../../CardPaymentForm';
import PixPayment, { type PixImage } from '../../../../PixPayment';
import { Alert, Badge } from '../../../../ui';
import { deadlineInfo, deadlineText, finalMessage, hasLivePix, parseOrderId, paymentPageState } from '../../../../online-payment';
import { useNow } from '../../../../use-now';

type OnlinePayment = { method: string; modality: string | null; status: string; raw_status?: string | null; pay_deadline?: string | null; rejection_message?: string | null; image?: PixImage | null };
type OrderDetail = { id: number; status: string; restaurant_id: number; restaurant_name: string; total_cents: number; payment: OnlinePayment | null };

const POLL_MS = 5000;

/**
 * Pagamento do pedido "Pagar agora" (spec de 11/10/2026, regras 10 e 11): prazo, recusa em destaque,
 * cartão ou Pix e cancelar, tudo numa tela. Antes vivia no Carrinho, atrás da barra inferior no celular.
 */
export default function PagamentoPedidoPage() {
  const params = useParams<{ id: string }>();
  const orderId = parseOrderId(params.id);
  const router = useRouter();
  const { user, busy } = useApp();
  const { cancelOrder } = useCustomer();
  const now = useNow(1000);
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [notFound, setNotFound] = useState(orderId === null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'card' | 'pix' | null>(null);
  const [config, setConfig] = useState<{ publicKey: string } | null>(null);
  const [working, setWorking] = useState(false);
  const [rejections, setRejections] = useState(0);
  const paidHere = useRef(false);
  const pixRequested = useRef(false);
  const alertRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async () => {
    if (orderId === null) return;
    try {
      const detail = await api<OrderDetail>(`/orders/${orderId}`);
      setOrder((previous) => {
        // Aprovado com a página aberta: mostra o aviso verde. Já pago ao abrir: volta para Pedidos.
        if (previous && previous.payment?.status !== 'paid' && detail.payment?.status === 'paid') paidHere.current = true;
        return detail;
      });
      setTab((current) => current ?? (detail.payment?.method === 'card' ? 'card' : 'pix'));
    } catch (failure) {
      if (failure instanceof ApiError && (failure.status === 404 || failure.status === 403)) setNotFound(true);
      else setError(failure instanceof Error ? failure.message : 'Não foi possível carregar o pedido.');
    }
  }, [orderId]);

  const payment = order?.payment ?? null;
  const deadline = deadlineInfo(payment?.pay_deadline, now);
  const state = order ? paymentPageState({ orderStatus: order.status, payment, payDeadlineOver: deadline?.over ?? false, paidHere: paidHere.current }) : null;

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { if (state === 'leave') router.replace('/loja/pedidos'); }, [state, router]);
  useEffect(() => {
    if (state !== 'pay' && state !== 'closing') return;
    const timer = window.setInterval(() => void load(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [state, load]);
  useEffect(() => {
    // A public key é da loja do pedido: cada loja recebe na própria conta.
    if (!order?.restaurant_id) return;
    let active = true;
    api<{ publicKey?: string }>(`/payments/public-config?restaurantId=${order.restaurant_id}`)
      .then((data) => { if (active) setConfig({ publicKey: data.publicKey ?? '' }); })
      .catch(() => { if (active) setConfig({ publicKey: '' }); });
    return () => { active = false; };
  }, [order?.restaurant_id]);
  useEffect(() => {
    if (payment?.status === 'rejected') alertRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [payment?.status, rejections]);

  const generatePix = useCallback(async () => {
    if (orderId === null) return;
    setWorking(true); setError('');
    try {
      await api(`/orders/${orderId}/payment/online`, { method: 'POST', body: JSON.stringify({ method: 'pix' }) });
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Não foi possível gerar o Pix.');
    } finally { setWorking(false); }
  }, [orderId, load]);

  useEffect(() => {
    // Pedido feito com Pix: gera o código ao abrir, uma vez. Depois de uma troca, só pelo botão.
    if (state !== 'pay' || tab !== 'pix' || pixRequested.current || !payment) return;
    if (payment.method !== 'pix' || hasLivePix(payment) || payment.status !== 'pending') return;
    pixRequested.current = true;
    void generatePix();
  }, [state, tab, payment, generatePix]);

  async function chargeCard(data: CardChargeData): Promise<'paid' | 'rejected' | 'pending'> {
    setError('');
    try {
      const result = await api<{ status?: string }>(`/orders/${orderId}/payment/online`, {
        method: 'POST',
        body: JSON.stringify({ method: 'card', cardToken: data.token, paymentMethodId: data.paymentMethodId, installments: data.installments, docType: data.docType, docNumber: data.docNumber }),
      });
      await load();
      if (result.status === 'rejected') { setRejections((count) => count + 1); return 'rejected'; }
      return result.status === 'paid' ? 'paid' : 'pending';
    } catch (failure) {
      // Ex.: 409 "Não foi possível cancelar o Pix gerado; tente de novo em instantes" — nada foi cobrado.
      setError(failure instanceof Error ? failure.message : 'Não foi possível concluir o pagamento com cartão.');
      await load();
      return 'rejected';
    }
  }

  async function cancel() {
    if (orderId === null) return;
    if (await cancelOrder(orderId)) await load();
  }

  if (notFound) return <section className="pay-page">
    <div className="pay-final" role="status"><p>{'Pedido não encontrado.'}</p><Link className="customer-solid-button" href="/loja/pedidos">{'Ver meus pedidos'}</Link></div>
  </section>;
  if (!order || !state || state === 'leave' || !payment) return <section className="pay-page">
    {error ? <Alert tone="error">{error}</Alert> : <p className="form-help">{'Carregando o pedido…'}</p>}
  </section>;

  const final = finalMessage(state, payment.status, order.restaurant_name);
  const livePix = hasLivePix(payment);

  return <section className="pay-page">
    <header className="customer-card pay-head">
      <span className="customer-kicker">{`Pedido #${order.id} · ${order.restaurant_name}`}</span>
      <h1>{money(order.total_cents)}</h1>
      {(state === 'pay' || state === 'closing') && <Badge tone="warning">{'Aguardando pagamento'}</Badge>}
      {state === 'pay' && deadline && <p className={`pay-deadline${deadline.urgent ? ' is-urgent' : ''}`}>{deadlineText('Pague até', deadline)}</p>}
    </header>

    {state === 'pay' && payment.status === 'rejected' && <div ref={alertRef} className="pay-rejected" role="alert">
      <strong>{'O cartão foi recusado. Nenhum valor foi cobrado.'}</strong>
      {payment.rejection_message && <span>{payment.rejection_message}</span>}
    </div>}
    {error && <Alert tone="error">{error}</Alert>}
    {state === 'closing' && <Alert tone="warning">{'O prazo para pagar acabou. Conferindo o pedido…'}</Alert>}

    {state === 'pay' && <>
      <div className="cart-segment" role="tablist" aria-label="Forma de pagamento">
        <button type="button" role="tab" aria-selected={tab === 'card'} className={tab === 'card' ? 'selected' : ''} onClick={() => setTab('card')}>{'Cartão'}</button>
        <button type="button" role="tab" aria-selected={tab === 'pix'} className={tab === 'pix' ? 'selected' : ''} onClick={() => setTab('pix')}>{'Pix'}</button>
      </div>
      {tab === 'card' && <>
        {livePix && <Alert tone="info">{'O Pix gerado será cancelado.'}</Alert>}
        {config
          ? <CardPaymentForm amountCents={order.total_cents} publicKey={config.publicKey} payerEmail={user?.email} onSubmit={chargeCard} />
          : <p className="form-help">{'Carregando…'}</p>}
      </>}
      {tab === 'pix' && (livePix
        ? <PixPayment image={payment.image} title="Pague com Pix" hint="Aguardando a confirmação do banco…" />
        : <div className="customer-card customer-online-payment">
            <p className="form-help">{'Gere o código e pague pelo app do seu banco.'}</p>
            <button type="button" className="customer-solid-button" disabled={working} onClick={() => void generatePix()}>{working ? 'Gerando…' : 'Gerar o Pix'}</button>
          </div>)}
      <footer className="pay-foot">
        <button type="button" className="orders-cancel" disabled={busy || working} onClick={() => void cancel()}>{'Cancelar pedido'}</button>
      </footer>
    </>}

    {final && <div className={`pay-final${state === 'approved' ? ' is-approved' : ''}`} role="status">
      <p>{final.text}</p>
      <Link className="customer-solid-button" href={final.href}>{final.button}</Link>
    </div>}
  </section>;
}
```

- [ ] **Passo 3: CSS no fim de `loja.css`**

```css
/* Página de pagamento do pedido (spec de 11/10/2026, regras 10 e 11). O respiro de baixo deixa o
   "Cancelar pedido" acima da barra inferior do celular. */
.pay-page { display: grid; gap: 16px; max-width: 640px; margin: 0 auto; padding: 16px 16px 120px; }
.pay-head { display: grid; gap: 6px; justify-items: start; }
.pay-head h1 { margin: 0; }
.pay-deadline { margin: 0; font-weight: 600; }
.pay-deadline.is-urgent { color: #b42318; }
.pay-rejected { display: grid; gap: 4px; padding: 14px 16px; border: 1px solid #f04438; border-radius: 12px; background: #fef3f2; color: #912018; }
.pay-foot { display: flex; justify-content: center; }
.pay-final { display: grid; gap: 12px; justify-items: start; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
.pay-final p { margin: 0; }
.pay-final.is-approved { border-color: #12b76a; background: #ecfdf3; color: #05603a; }
[data-theme='dark'] .pay-deadline.is-urgent { color: #fda29b; }
[data-theme='dark'] .pay-rejected { background: #3b1513; color: #fecdca; }
[data-theme='dark'] .pay-final.is-approved { background: #053321; color: #a6f4c5; }
```

- [ ] **Passo 4: tipos**

Rodar: `npx tsc --noEmit` — esperado: sem saída. (O Carrinho ainda chama o `CardPaymentForm` antigo e vai
acusar erro de tipo em `chargeCardOrder`; isso some na Tarefa 6. Se acusar, faça as Tarefas 5 e 6 antes de
rodar o `tsc` e use um commit só para as duas.)

- [ ] **Passo 5: commit**

```bash
git add "platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx" platform/apps/web/app/CardPaymentForm.tsx platform/apps/web/app/loja.css
git commit -m "feat(web): página de pagamento do pedido com prazo, recusa em destaque, cartão, Pix e cancelar"
```

---

### Tarefa 6: Carrinho leva à página nova; Pedidos mostra "Aguardando pagamento" e "Pagar" (regras 12 e 13)

**Arquivos:**
- Alterar: `platform/apps/web/app/loja/customer-context.tsx` (tira `onlineCode`, `cardOrder`, `chargeCardOrder`; `placeOrder` navega)
- Alterar: `platform/apps/web/app/loja/carrinho/page.tsx:3-9, 27, 32, 66-67, 175`
- Alterar: `platform/apps/web/app/loja/pedidos/page.tsx:13-23, 50-70`

**Interfaces:**
- Consome: `awaitingOnlinePayment` (Tarefa 1); a rota `/loja/pedidos/[id]/pagamento` (Tarefa 5).
- Remove do `CustomerValue`: `onlineCode`, `cardOrder`, `chargeCardOrder`, `publicKey`. Mantém `CardChargeData` exportado (a página e o `CardPaymentForm` usam).

- [ ] **Passo 1: `customer-context.tsx`**

1. Tirar dos tipos e do `value`: `onlineCode`, `cardOrder`, `chargeCardOrder`, `publicKey`. Tirar os
   `useState` de `onlineCode`, `cardOrder` e `publicKey` e a função `chargeCardOrder` inteira. No efeito de
   `/payments/public-config`, tirar as chamadas a `setPublicKey`.
2. Importar `useRouter`: `import { usePathname, useRouter } from 'next/navigation';` e, junto do
   `pathname`, `const router = useRouter();` e `const leaving = useRef(false);`.
3. Em `placeOrder`, na linha `setPlacing(true); setLocalMessage(''); setOnlineCode(null); setCardOrder(null);`
   ficar só `setPlacing(true); setLocalMessage('');`. O ramo `else if (modality === 'online') { … }` inteiro vira:

```ts
      } else if (modality === 'online') {
        // Cartão e Pix são pagos na página do pedido: prazo, recusa e troca de forma ficam lá (regra 10).
        leaving.current = true;
        router.push(`/loja/pedidos/${order.id}/pagamento`);
        return;
      } else {
```

   e o `finally` vira `finally { if (!leaving.current) setPlacing(false); }`.
4. Novo efeito, depois dos outros:

```ts
  // Saindo do Carrinho para o pagamento, o "Processando…" fica até a página nova abrir (sem o
  // "Seu carrinho está vazio" no meio do caminho).
  useEffect(() => { if (leaving.current) { leaving.current = false; setPlacing(false); } }, [pathname]);
```

- [ ] **Passo 2: `carrinho/page.tsx`**

Tirar os imports de `CardPaymentForm` e `PixPayment`; `const { busy } = useApp();`; tirar `onlineCode`,
`publicKey`, `cardOrder`, `chargeCardOrder` do `useCustomer()`; apagar as linhas 66 e 67 (`PixPayment` e
`CardPaymentForm`). O rótulo do botão (linha 40) fica
`!manual && modality === 'online' ? 'Fazer pedido e pagar' : 'Fazer pedido'`. O título do carrinho vazio
(linha 175):

```tsx
      <h2>{placing ? 'Abrindo o pagamento…' : cartLoaded ? 'Seu carrinho está vazio' : 'Carregando seu carrinho...'}</h2>
      {cartLoaded && !placing && <Link className="customer-solid-button m-shine" href="/loja">{'Explorar restaurantes'}<Icon name="arrow-right" /></Link>}
```

- [ ] **Passo 3: `pedidos/page.tsx`**

Tirar `payMethods`, `payStatuses` e a função `paymentLabel` locais (linhas 18 a 23) e importar a do
contexto, que já traduz `rejected`, `expired` e o online pendente:

```ts
import { Order, money, paymentLabel, useApp } from '../../app-context';
import { awaitingOnlinePayment } from '../../online-payment';
```

Em `ActiveOrder`:

```tsx
  const awaiting = awaitingOnlinePayment(order);
```

A linha de status: `<p className="orders-status-line"><span className="m-live-dot" />{awaiting ? 'Aguardando pagamento' : statusLabels[order.status] ?? order.status}</p>`

Em `orders-actions`, antes do "Cancelar pedido":

```tsx
      {awaiting && <Link className="customer-solid-button" href={`/loja/pedidos/${order.id}/pagamento`}>{'Pagar'}</Link>}
```

- [ ] **Passo 4: tipos e build**

Rodar (em `platform/apps/web`): `npx tsc --noEmit` — esperado: sem saída.
Rodar: `npm run build` — esperado: termina com a lista de rotas, incluindo `/loja/pedidos/[id]/pagamento`.

- [ ] **Passo 5: conferir no navegador (dev local)**

Pedido "Pagar agora" com Pix: "Fazer pedido e pagar" leva direto à página nova, sem passar por "Seu
carrinho está vazio"; a página mostra o QR, "Aguardando a confirmação do banco…" e "Pague até". Em
Pedidos, o pedido aparece com "Aguardando pagamento" e "Pagar". O cartão não carrega em `http://127.0.0.1`
(regra do Mercado Pago, já avisada pelo formulário): conferir só a mensagem. Prints.

- [ ] **Passo 6: commit**

```bash
git add platform/apps/web/app/loja/customer-context.tsx platform/apps/web/app/loja/carrinho/page.tsx platform/apps/web/app/loja/pedidos/page.tsx
git commit -m "feat(web): carrinho leva ao pagamento do pedido; Pedidos mostra aguardando pagamento e Pagar"
```

---

### Tarefa 7: Documentação, verificação, PR e aceite no staging

**Arquivos:**
- Alterar: `docs/ESTADO_ATUAL.md` (linha "Em revisão" e o parágrafo "Andamento")

- [ ] **Passo 1: verificação completa**

Rodar (em `platform`): `pnpm verify`
Esperado: todos os passos OK, incluindo "Testes do site (node --test)". Anotar o número de testes Java.

- [ ] **Passo 2: `ESTADO_ATUAL.md`**

Na linha "Em revisão", acrescentar a PR 2 (`feat/pagamento-online-telas`: página de pagamento do pedido,
diálogo de motivo no painel, cartão do pedido com prazos), com o link do PR quando aberto. No parágrafo
"Andamento", trocar "a **PR 2 (…)** vem depois" por "a **PR 2** está em revisão".

- [ ] **Passo 3: commit e PR (só com pedido explícito do usuário)**

```bash
git add docs/ESTADO_ATUAL.md
git commit -m "docs: ESTADO_ATUAL com a PR 2 do pagamento online em revisão"
```

Push e PR só quando o usuário pedir. Enquanto a PR 1 (#31) não estiver mesclada: PR em **rascunho** com
base `feat/pagamento-online-clareza`; depois do merge, trocar a base para `main`.

- [ ] **Passo 4: aceite no staging (depois do deploy, que só sai da `main`)**

Roteiro de 11/10 repetido **inteiro no navegador do app**, com print legível de cada tela: recusa (aviso
vermelho no topo, sem "HTTP 402"), nova tentativa com outro cartão, Pix, troca de Pix para cartão (aviso
"O Pix gerado será cancelado."), estorno automático, Recusar e Estornar pela loja pelo diálogo, prazo
vencido. Conferir também o **fuso** (Foco da revisão, item 1): "Pague até" = horário do pedido + 15 min.
