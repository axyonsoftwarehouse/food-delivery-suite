# Plano do teste de carga

Criado em **07/11/2026**. Item **B9** de `CHECKLIST_LANCAMENTO.md` (prioridade máxima).
Estado (07/11): **dados de carga criados no staging e validação (nível 0) aprovada lá**
(`platform/load/README.md`). Falta combinar a janela e fazer as rodadas, a começar pelo nível 1.

**Staging, 07/11 (commit `5441efb`):** o `load:setup` criou 20 lojas (ids 8 a 27), 500 clientes,
90 entregadores e 10 mil pedidos de histórico (500 por loja) em 52 s. A senha das contas de carga é
própria (arquivo `%USERPROFILE%\.foodie\carga-senha` no PC do Werner), porque o `DEMO_PASSWORD` está
vazio no staging. O nível 0 contra o staging teve 364 chamadas, 0% de erros e um pedido criado. Essa
rodada achou um defeito do próprio script, já corrigido: o visitante "sem cache" montava endereços de
arquivos estáticos colados (404).

> **Convenção.** Os números de carga deste plano são **palpite** (não houve levantamento com
> lojas reais) e estão marcados assim. Quando houver dado real — lojas fechadas, pedidos por noite —
> troque o palpite e refaça a conta da §2.

## 1. Para que serve

Saber, **antes de abrir ao público**, quanto a VPS aguenta e **o que quebra primeiro**, com folga para
o primeiro mês. A produção vai rodar na VPS atual (decisão de 07/11), então o teste roda no staging,
na mesma máquina e com a mesma configuração que a produção vai ter.

Sai do teste:

- a capacidade medida (clientes simultâneos e pedidos por minuto) dentro das metas da §4;
- o primeiro gargalo e a correção (configuração ou código), com o teste repetido depois dela;
- a confirmação de que nada vaza memória numa noite inteira de movimento (teste de resistência).

Não é objetivo: medir o Mercado Pago (os pedidos de carga são **em dinheiro**), testar o app da cozinha
(Expo) ou dimensionar para centenas de lojas.

## 2. Carga esperada (palpite)

Primeiro mês, uma cidade, pico de sexta das 19h às 21h:

| Premissa | Palpite | Conta |
| --- | --- | --- |
| Lojas ativas | 20 | — |
| Pedidos por loja no pico | 15 por hora | 20 × 15 = **300 pedidos/h ≈ 5 por minuto** |
| Clientes com o site aberto ao mesmo tempo | **150** (metade logada) | ~10–15% de conversão sobre ~2.000 visitas/h, ~5 min por visita |
| Telas de loja abertas (painel + cozinha) | **40** | 20 lojas × 2 |
| Entregadores na rua | **30** (metade em entrega) | 1,5 por loja |

> **Atualizado em 11/11:** a S18 etapa 1 (`app/poll-plan.ts`) mudou o ritmo descrito abaixo. A cada
> 8 s vão só os pedidos (e os entregadores, para a loja); o resto vai a cada 3 min, e o cliente sem
> pedido em andamento consulta a cada 30 s. O `load/cenarios.js` já segue o ritmo novo. O texto
> abaixo é o de 07/11, base das contas da rodada de 11/11.

**O que pesa de verdade é a consulta automática, não o clique.** Todo usuário logado com a aba
visível chama o `refresh()` do `app-context.tsx` a cada **8 s**:

- **cliente:** 5 chamadas (`/catalog/meta`, `/orders`, `/zones`, `/addresses`, `/me/permissions`);
- **loja/cozinha:** 6 chamadas, uma delas o **catálogo inteiro** (`/catalog`), mais `/orders`,
  `/restaurant/couriers`, `/zones`, `/me/permissions` e `/restaurant/modules`;
- mais o sino (`/notifications`, a cada 15 s), as mensagens não lidas do chat
  (`/orders/chat/unread`, 8 s) e, no acompanhamento do pedido, o código de entrega (8 s) e a conversa
  aberta (5 s).

Só os 75 clientes logados do palpite dão ~47 chamadas/s, e as 40 telas de loja, ~30 chamadas/s,
antes de qualquer navegação. Os entregadores mandam a posição a cada 15 s em entrega e a cada 30 s em
turno parado.

## 3. Níveis do teste

| Nível | Clientes simultâneos | Pedidos/min | Telas de loja | Entregadores | Duração |
| --- | --- | --- | --- | --- | --- |
| **1. Esperado** | 150 | 5 | 40 | 30 | 15 min (+3 min de subida) |
| **2. Folga (3×)** | 450 | 15 | 120 | 90 | 15 min (+5 min de subida) |
| **3. Ruptura** | sobe de 150 em 150 a cada 3 min | proporcional | proporcional | proporcional | até furar a meta (§4) ou chegar a 1.500 |
| **4. Resistência** | 150 | 5 | 40 | 30 | **60 min** |

Ordem: 1 → corrigir o que aparecer → 2 → 3 → 4. Se o nível 1 já furar a meta, parar e corrigir antes
de seguir.

## 4. Metas (palpite)

| Medida | Meta |
| --- | --- |
| Leituras (catálogo, loja, pedidos, consultas automáticas): p95 | < 800 ms (a mesma do `pnpm load`) |
| Páginas do site renderizadas no servidor (home, página da loja): p95 | < 1,5 s |
| Fechar o pedido (`POST /cart/checkout`): p95 | < 2 s |
| Erros (5xx, tempo esgotado, conexão recusada) | < 1% |
| Containers | nenhum reinício, nenhum processo morto por falta de memória |
| Resistência (nível 4) | memória da API e do banco estável na última meia hora (sem subir sem parar) |

O nível 2 precisa passar para o lançamento. O nível 3 só mede até onde dá.

## 5. Como o teste é montado

**Ferramenta: k6** (Grafana Labs): scripts em JavaScript, subida gradual, metas que reprovam o teste
sozinhas e relatório por tipo de chamada. Roda **do PC do Werner** contra o staging. Rodar dentro da
VPS falsearia o resultado, porque o gerador de carga disputaria a CPU com o sistema medido.
O `pnpm load` atual (`platform/scripts/load-test.mjs`) continua como verificação rápida e não é
substituído.

**Os usuários virtuais imitam o navegador:** chamam o site pelo `/backend/*` (passando pelo Next,
como o navegador faz), mandam o `Origin` do site nas escritas (o `OriginCheckFilter` exige) e usam o
cookie `foodie_session`.

Perfis (cada um vira um cenário do k6):

1. **Visitante** — home, página pública da loja, cardápio (`/catalog/search`, ficha do prato), CEP;
   sem login. Pausa de 3 a 10 s entre ações.
2. **Cliente logado navegando** — o mesmo do visitante, mais a consulta automática de 8 s e o sino.
3. **Comprador** — entra, monta o carrinho (`PATCH /cart/items/...`), fecha em dinheiro
   (`POST /cart/checkout`) e fica no acompanhamento (consulta automática, código de entrega e chat)
   por alguns minutos. Ritmo controlado para dar os pedidos/min da §3 (cenário de taxa constante).
4. **Tela da loja** — consulta automática de 6 chamadas a cada 8 s. Uma das telas por loja **aceita**
   os pedidos, para exercitar as mudanças de status (senão todos expiram em 15 min).
5. **Entregador** — abre o turno e manda a posição (`POST /courier/location`) a cada 15–30 s.

**O login acontece uma vez por usuário virtual**, na preparação. A senha usa SCrypt e custa CPU de
propósito: um login a cada clique mediria o hash, não o sistema. Uma rodada curta à parte mede o pico
de login (muita gente entrando ao mesmo tempo).

## 6. Preparação dos dados

Tudo numa **loja só de carga**, para não misturar com as lojas de demonstração. Pedido novo manda
push para os aparelhos inscritos da loja, então as lojas de demonstração lotariam o celular de quem
estiver inscrito.

1. **Loja "Carga Foodie"** criada **pela API** com o admin de demonstração (`POST /admin/restaurants`,
   usuário da loja, horário 24 h, cobertura de CEP e entrega), com cardápio de ~40 pratos pela
   importação CSV (`/restaurant/catalog/import`, a mesma do `smoke:import`), alguns com opções e
   adicionais. Ninguém da loja inscreve push. Um script de preparação faz isso e pode ser rodado de
   novo sem duplicar.
2. **Clientes e entregadores de carga** (500 clientes, 90 entregadores da loja de carga) criados **por
   SQL na VPS**. O cadastro pela API conta também os cadastros bem-sucedidos no limite por IP
   (`AuthService`), e o teste sai de um IP só. As contas reaproveitam o hash de senha de uma conta de
   demonstração, então a senha é a da demonstração. Ela entra no k6 por variável de ambiente
   (`LOAD_PASSWORD`) e nunca vai para o repositório. **Escrita no banco: o Werner roda ou autoriza.**
3. **Volume de histórico (opcional, recomendado):** alguns milhares de pedidos antigos na loja de
   carga, para o `/orders` da tela da loja não ser medido com a lista vazia. Se a lista de pedidos não
   tiver paginação, esse é um suspeito forte `(a confirmar)`.

**Limpeza:** um SQL apaga pedidos, contas e a loja de carga depois. A produção nasce com banco limpo
(item B6), mas o staging continua em uso.

## 7. Antes, durante e depois

**Antes:** combinar a janela (ninguém testando no staging), backup manual do banco
(`RUNBOOK_VPS.md` §5), conferir o disco (abaixo de 70%) e avisar a equipe.

**Durante, na VPS** (anotar a cada nível):

- `docker stats` — CPU e memória por container (api, web, db, caddy);
- `free -m` e `vmstat 5` — memória livre e uso de swap;
- MariaDB: `SHOW GLOBAL STATUS LIKE 'Threads_connected'`, `Threads_running` e o log de consultas
  lentas, se ligado;
- pool de conexões da API (Hikari): conexões ativas e esperando. O actuator só responde na rede
  interna (o Caddy devolve 404 por fora);
- Sentry (`foodie-api` e `foodie-web`) — erros novos durante a janela.

**Depois:** resultado em `docs/RESULTADO_TESTE_CARGA_<data>.md` (números por nível, gargalo, correção,
repetição) e o item B9 atualizado.

## 8. Suspeitos, antes de medir

Achados na leitura do código e da configuração em 07/11. São hipóteses, e o teste confirma ou descarta:

1. **Pool de conexões com o banco com 8 conexões** (`spring.datasource.hikari.maximum-pool-size: 8`).
   Com a consulta automática de centenas de telas, é o candidato mais forte a primeiro gargalo:
   as chamadas começam a esperar conexão e o p95 dispara sem a CPU estar alta.
   **Confirmado em 12/11** no nível 2 (fila de até 116 chamadas); o pool passou para 24 na branch
   `perf/pool-conexoes`.
2. **JVM sem limite de memória definido** (`java -jar app.jar`, sem `-Xmx`): ela usa o padrão (1/4 da
   memória da máquina, ~1 GB de 4 GB `(a confirmar o plano da VPS)`).
3. **Nenhum container tem limite de memória** (`docker-compose.yml`). Num pico, a falta de memória
   pode matar qualquer processo, inclusive o MariaDB.
4. **Consulta automática de 8 s para o cliente** buscando `/catalog/meta` e `/zones`, que quase nunca
   mudam. Se pesar, dá para espaçar ou cortar essas duas para o cliente: é uma mudança pequena no site.
5. **`/catalog` inteiro na tela da loja a cada 8 s** — cresce com o cardápio.
6. **Next renderizando a home e a página da loja** a cada visita, se essas páginas não tiverem cache
   `(a confirmar)`.

## 9. O que o Werner precisa fazer

1. Instalar o k6 no PC (`winget install k6 --source winget`).
2. Autorizar (ou rodar) o SQL que cria e depois apaga as contas de carga na VPS.
3. Escolher a janela do teste e acompanhar a VPS durante a rodada, ou autorizar o acompanhamento por SSH
   (só leitura).

## 10. O que foi construído

- `platform/load/cenarios.js` — os cinco perfis da §5, os níveis da §3 (`NIVEL=0..4`, sendo 0 a
  validação) e as metas da §4 como limites que reprovam o teste;
- `platform/tools/src/load-setup.ts` — em vez do SQL solto previsto antes, a preparação reaproveita a
  ferramenta de seed (conexão e hash de senha compatível com a API). Cria 20 lojas de carga (não uma
  só: cada loja recebe a sua fatia dos pedidos, como na vida real), com vitrine publicada, cardápio de
  20 pratos, duas contas por loja, clientes com endereço na zona de carga, entregadores aprovados e,
  opcionalmente, histórico de pedidos;
- `platform/tools/src/load-clean.ts` — a limpeza, que descobre as dependências pelas chaves
  estrangeiras;
- `platform/load/README.md` — como rodar, passo a passo.

**Validação local (07/11):** a preparação rodou duas vezes sem duplicar (3 lojas, 10 clientes,
4 entregadores e 15 pedidos de histórico), e o nível 0 rodou contra o site local. A primeira rodada
achou duas falhas, corrigidas: a API local estava desatualizada (a busca só por loja é do cardápio sem
login) e a vitrine pública das lojas de carga não existia (404).
