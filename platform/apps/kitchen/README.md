# Foodie Cozinha (KDS)

Aplicativo **React Native (Expo + TypeScript)** para a cozinha do restaurante. É uma
aplicação operacional separada: mostra a fila de pedidos e permite **aceitar**, **marcar
como pronto** e **recusar** pedidos, reaproveitando a API Java do Foodie.

A decisão e o racional estão em `../../../docs/PLANO_APPS_MOBILE.md`.

## O que o app faz

- Quadro em três colunas: **Novos** (`placed`), **Em preparo** (`accepted`) e
  **Prontos** (`ready`, `assigned`, `picked_up`).
- Ações por etapa: aceitar/recusar em `placed`; marcar pronto em `accepted`.
- Detalhe do ticket: itens, variações, adicionais, endereço, agendamento e histórico.
- Destaque de pedido **atrasado** (> 10 min sem aceite), cronômetro e valor.
- Atualização a cada 8 s e **push FCM** para novo pedido.

## Papéis

O KDS entra com um usuário de **cozinha** (`kitchen`) ou do **restaurante**
(`restaurant`). O papel `kitchen` é criado pelo admin (painel → Equipe e acessos) e só
pode ler pedidos do restaurante e executar `accept`, `ready` e `reject`.

## Como rodar

```bash
# na raiz de platform/
pnpm install

# vars de ambiente (sem segredos no bundle)
#   EXPO_PUBLIC_API_URL=http://127.0.0.1:4001
pnpm --filter @foodie/kitchen start
```

Comandos úteis:

```bash
pnpm --filter @foodie/kitchen typecheck
pnpm --filter @foodie/kitchen test
```

## Contrato da API (reaproveitado)

| Uso | Endpoint |
| --- | --- |
| Login | `POST /auth/login` → corpo `User` + header `X-Foodie-Token` |
| Autorização | `Authorization: Bearer <token>` |
| Fila | `GET /orders` (escopado por papel) |
| Ticket | `GET /orders/{id}` (`items`, `history`, `payment`) |
| Ação | `PATCH /orders/{id}/status` `{ action: accept \| ready \| reject, reason? }` |
| Push | `POST /notifications/device-tokens` `{ token, platform }` |

## Estrutura

- `src/api` — cliente HTTP, tipagens e chamadas.
- `src/auth` — sessão (token em `expo-secure-store`).
- `src/domain/orders.ts` — regras puras da fila (colunas, ações, atraso).
- `src/components`, `src/screens` — UI.
- `src/notifications` — registro de token e listeners do FCM.

## Pendências conhecidas

- Testes de componente (RNTL) desativados por conflito de resolvedor de módulos com o
  layout do pnpm; os testes de domínio cobrem as regras da fila.
- Impressão térmica adiada (exige módulo nativo ESC/POS).
- `EXPO_PUBLIC_API_URL` de homologação ainda a confirmar.
