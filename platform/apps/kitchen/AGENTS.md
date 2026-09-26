# AGENTS — Foodie Cozinha (app da cozinha)

Aplicativo Expo/React Native (TypeScript). Priorize padrões mobile, desempenho e
compatibilidade de plataforma.

## Stack

- **Expo SDK 57** + React Native 0.86 + React 19.
- **React Navigation** (native-stack) para navegação — decisão registrada em
  `docs/PLANO_APPS_MOBILE.md`. Não use Expo Router aqui.
- **TanStack Query** para dados de servidor (polling de 8 s) e **Zustand** para estado
  local (sessão).
- **i18next** (pt-BR). Sessão em **expo-secure-store**. Push em **expo-notifications**
  (FCM via `getDevicePushTokenAsync`).

## Antes de codar

- Expo muda APIs a cada SDK. Leia a versão em `package.json` e consulte
  `https://docs.expo.dev/versions/v57.0.0/` antes de usar APIs novas.
- Use sempre `pnpm --filter @foodie/kitchen exec expo install <pacote>` para dependências
  nativas (resolve a versão compatível com o SDK).

## Comandos

```bash
pnpm --filter @foodie/kitchen typecheck   # tsc --noEmit
pnpm --filter @foodie/kitchen test        # jest
pnpm --filter @foodie/kitchen start       # expo start
```

Rode typecheck e testes antes de considerar a tarefa concluída.

## Regras de produto

- O papel de cozinha (`kitchen`) só executa `accept`, `ready` e `reject`, e só enxerga
  pedidos do próprio restaurante. Não adicione ações de admin/entregador aqui.
- Não embuta segredos; a URL da API vem de `EXPO_PUBLIC_API_URL`.
