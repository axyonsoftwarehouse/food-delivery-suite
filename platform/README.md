# Plataforma Foodie

Plataforma própria de delivery multirrestaurante. O **lojista** opera a própria loja e é
dono da venda; a **Foodie** cobra **assinatura**, não comissão. O mesmo site atende
cliente, restaurante, administração e entregador — o papel vem do login.

O código do produto vive aqui: **Java 21 / Spring Boot 3.5** (`apps/api-java`), **Next.js**
(`apps/web`), MariaDB própria e Flyway. O protótipo TypeScript e o pacote legado de
terceiros não existem mais neste repositório.

## Como rodar localmente

1. Copie `platform/.env.example` para `platform/.env` e defina `DB_PASSWORD` e
   `DB_ROOT_PASSWORD`.
2. Copie `platform/tools/.env.example` para `platform/tools/.env` (mesma senha do usuário
   `foodie` e um `DEMO_PASSWORD` com 12+ caracteres).
3. Com o Docker ativo, na pasta `platform`:

```bat
docker compose up -d db                         :: MariaDB em 127.0.0.1:3307
pnpm install
docker compose --profile java up -d --build     :: API em 127.0.0.1:4001; o Flyway migra na subida
pnpm --filter @foodie/tools seed                :: dados de demonstração (exige DEMO_PASSWORD)
pnpm dev                                        :: site em http://127.0.0.1:3001
```

Contas do seed (senha em `DEMO_PASSWORD`): `admin@demo.local`, `restaurante@demo.local`,
`restaurante2@demo.local`, `doceria@demo.local`, `entregador@demo.local`,
`cliente@demo.local` e `cliente2@demo.local`. O seed não redefine a senha de usuário
existente.

## Verificação

```bat
pnpm verify                       :: testes Java + site + cozinha + tipos + build + Compose
VERIFY_INTEGRATION=1 pnpm verify  :: banco/API efêmeros + seed + smokes dos papéis
pnpm load                         :: teste de carga contra a API
```

O `pnpm verify` roda a checagem de variáveis do Compose (`scripts/check-compose-env.mjs`):
toda variável lida pela API precisa estar declarada no serviço da API dos dois
`docker-compose`. O CI roda `VERIFY_INTEGRATION=1` em cada PR.

## O que existe hoje

- **Cliente:** home pública, cadastro/login (e-mail, telefone/OTP e login social Google e
  Facebook), endereço por zona/CEP, catálogo com busca, variações, adicionais, combos,
  estoque, agenda, cupons, carrinho persistente e checkout idempotente. Busca, tags e detalhe
  do prato ficam em cache local por 7 dias para consulta quando a rede falha.
- **Pedido:** entrega, retirada, consumo no local e PDV de balcão; estados normais e
  excepcionais; observação por item e no pedido; tempo estimado; chat do pedido entre
  cliente, loja e entregador; avaliações.
- **Loja:** painel, catálogo (com importação CSV), horários, equipe, entregadores, mesas, PDV,
  cozinha (KDS), promoções, financeiro, relatórios e assinatura.
- **Entregador:** PWA em `/entregas`, turno/posição, rota, confirmação por código,
  falha com motivo, reputação e extrato informativo.
- **Admin:** governança, suporte auditado, observabilidade (Sentry), backup e leitura das
  assinaturas/cobranças.

## Dinheiro

- **A venda é da loja.** Frete, gorjeta, cashback, estorno e chargeback são da loja; a
  Foodie não recebe, não guarda e não devolve dinheiro de pedido.
- **Pagamento online** (Pix/cartão) usa a conta Mercado Pago de cada loja, conectada por
  OAuth em Configurações → Recebimento online. Pix e cartão passam pelo checkout
  transparente; o cartão pode pedir 3DS. Sem conta conectada, a loja não oferece pagamento
  online. `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` libera o caminho de homologação.
- **Assinatura da loja** é a receita da Foodie: trial de 15 dias, cobrança Pix/cartão por
  ciclo na conta da Foodie, webhook idempotente, carência e expiração. Configuração em
  `SUBSCRIPTIONS_*`; sem `SUBSCRIPTIONS_ALLOW_REAL_CHARGES=true` e token, o job não cobra
  (e agora loga o motivo no start e nas rodadas).
- **Pagamento na entrega** continua disponível e é confirmado pela loja/entregador.

## Deploy e operação

- **Homologação:** `https://staging.2.29.42.104.sslip.io` (API em
  `https://api.staging.2.29.42.104.sslip.io`). O schema atual aparece em `/ready`.
- **Publicar:** botão **Deploy - Foodie** no GitHub (exige `PUBLICAR`) ou
  `platform/deploy/release.ps1`; ambos exigem a `Verificação` verde. O deploy tem backup,
  conferência de `/ready`/schema e rollback.
- **Operação da VPS:** `docs/RUNBOOK_VPS.md`. **Estado do projeto:**
  `docs/ESTADO_ATUAL.md`. **Deploy em detalhe:** `platform/deploy/README.md`.
- **Configuração:** `platform/.env.example` (local) e `platform/deploy/.env.example` (VPS)
  listam as chaves. O `.env` da VPS não é sobrescrito pelo deploy.

## Limites antes de produção

A base é de homologação, não autorização de exposição comercial. Ainda faltam: provedor
SMTP real configurado/testado (o código e o repasse de `MAIL_*`/`SPRING_MAIL_*` existem),
revisão de origem/CSRF nos domínios finais, ensaio de restauração do backup do R2, teste de
carga na VPS, troca de todas as credenciais de teste e o ambiente de produção (domínio,
conta Mercado Pago e chave Pix). Apple Sign In, carteira do cliente como meio de pagamento e
contrato dos módulos seguem no backlog (`docs/ESTADO_ATUAL.md` §4). A fronteira do super-admin
avançou em 03/11/2026: a Operação não fecha mais a loja, não edita tags nem perfil/turnos/
incentivos de entregador, e o extrato individual do restaurante saiu do admin.

## Organização

- `apps/api-java`: API Java/Spring Boot, Flyway, testes JUnit.
- `apps/web`: site Next.js (cliente, painel, cozinha e entregador).
- `tools`: seed e smokes em TypeScript.
- `deploy`: Compose de homologação, `release.ps1`, `deploy.sh`, backup/restore, backup R2,
  limpeza de disco e monitoramento.
- `monitoring`: Prometheus + Alertmanager + Blackbox.
- `tests`: smokes HTTP (`cart`, `catalog`, `exceptions`, `auth`, `coverage`, `subscription`).
