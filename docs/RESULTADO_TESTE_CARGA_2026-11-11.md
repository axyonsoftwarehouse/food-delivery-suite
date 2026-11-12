# Resultado do teste de carga — 11/11/2026

Plano: `PLANO_TESTE_CARGA.md`. Item **B9** de `CHECKLIST_LANCAMENTO.md`.

**Resumo:** o nível 0 passou; o **nível 1 reprovou** no primeiro minuto. Cerca de 15 logins
simultâneos esgotam a memória da API (SCrypt de 64 MiB por login contra um heap de 956 MB), o
`OutOfMemoryError` mata a thread do Tomcat que aceita conexões e a API fica **morta até ser
reiniciada** (CPU em 0%, todas as chamadas em tempo esgotado). Os níveis 2 a 4 não foram rodados:
o plano manda corrigir antes de seguir.

## Ambiente

| Item | Valor |
| --- | --- |
| Código | `71ac37c` (merge da PR #127, taxa por bairro), schema `080`, deploy às 18:47 UTC |
| Dados | `load:setup` refeito depois do deploy: 20 lojas (ids 8–27), 500 clientes, 90 entregadores, 500 pedidos de histórico por loja; zona de carga #3 |
| Antes | backup `foodie_platform-20261111-184951.sql`; disco em 55% (limpeza de 125 imagens antigas do StoryVerse, de 77% para 44%, antes do deploy); seed de demonstração rodado uma vez (grava os CEPs de demonstração no cache) |
| Gerador | k6 v2.3.0 no PC do Werner, contra `https://staging.2.29.42.104.sslip.io` |
| VPS | 3,8 GB de RAM, 1 GB de swap já em uso em repouso |

## Nível 0 — validação (passou)

342 chamadas, **0% de erros**, 14/14 verificações, 1 pedido criado (já no modelo de taxa por
bairro). p95: leituras 508 ms, páginas 895 ms, fechamento do pedido 430 ms. A mediana das leituras
com um usuário só já é ~200 ms (rede PC → VPS incluída).

## Nível 1 — esperado (reprovou)

| Medida | Meta | Resultado |
| --- | --- | --- |
| Erros | < 1% | **71%** (3.168 de 4.462) |
| Leituras p95 | < 800 ms | **60 s** (tempo esgotado) |
| Páginas p95 | < 1,5 s | **21 s** |
| Logins | — | **0 de 1.864** |
| Containers | nenhum reinício | a API ficou `unhealthy` e só voltou com `docker compose restart api` |

**Linha do tempo (UTC):**

- 18:51–18:55 — subida; os logins de 200 ms passam a levar 2 s, depois 20–40 s.
- 18:55:26 — primeiro `java.lang.OutOfMemoryError: Java heap space` num `POST /auth/login`.
  A API estava em 168% de CPU e 1,1 GB de memória.
- 18:56 — `OutOfMemoryError thrown from the UncaughtExceptionHandler in thread
  "http-nio-0.0.0.0-4001-Poller"`. Sem o Poller, o Tomcat não aceita mais conexões.
- 18:56–19:14 — API em ~0% de CPU, banco ocioso, todas as chamadas em tempo esgotado.
- 19:2x — log salvo na VPS (`/tmp/api-oom-nivel1-20261111.log`) e API reiniciada; voltou saudável.

## Causa

1. `PasswordVerifier` usa SCrypt N=2^16, r=8: **64 MiB por login**, sem limite de logins simultâneos.
2. A JVM roda com `java -jar app.jar` (Dockerfile), sem `-Xmx`: o heap máximo é 25% da RAM,
   **956 MB** (`MaxHeapSize = 1002438656`). Confirma o suspeito 2 do plano.
3. ~15 logins simultâneos (15 × 64 MiB) bastam para esgotar o heap. Na subida do nível 1 havia
   dezenas ao mesmo tempo.
4. O `OutOfMemoryError` não derruba o processo: mata threads ao acaso, inclusive o Poller. O
   container continua "rodando" e o `restart: unless-stopped` nunca age.

**Isso também é um risco de produção, não só do teste:** uma rajada de ~15 logins (pico de
abertura, ou alguém de propósito) derruba a API até alguém reiniciá-la manualmente.

## Correção (branch `fix/limite-scrypt`)

As duas primeiras linhas abaixo estão implementadas nessa branch (`ScryptGate`, com 4 vagas e
espera de até 10 s, e o `Dockerfile` da API). Publicação no staging e nova rodada **(a confirmar)**.

- **Limitar os hashes SCrypt simultâneos** (semáforo no `PasswordVerifier`, ex.: 4 por vez; quem
  passa disso espera um pouco ou recebe 503). É a causa: o pico de memória fica limitado a ~256 MiB
  em qualquer carga.
- **`-XX:+ExitOnOutOfMemoryError`** no `ENTRYPOINT`: se a memória acabar por outro motivo, o
  processo sai e o Docker reinicia a API, em vez de ela ficar viva e surda.
- Heap explícito e limite de memória dos containers (suspeitos 2 e 3): decidir **depois** de medir
  de novo com a correção acima, com os números do nível 1 na mão.

Depois da correção: refazer nível 0 e nível 1, e seguir para 2 → 3 → 4.

## Segunda rodada — depois da PR #128 (`da8b3db`, 20:03–20:25 UTC)

Backup `foodie_platform-20261111-200231.sql` antes. Nível 0 passou (352 chamadas, 0% de erros).

**Nível 1: estável, mas reprovou em uma meta (leituras).**

| Medida | Meta | Resultado |
| --- | --- | --- |
| Erros | < 1% | **0,04%** (64 de 146.059) ✓ |
| Fechar o pedido p95 | < 2 s | **1,02 s** ✓ (84 pedidos criados) |
| Páginas p95 | < 1,5 s | **1,19 s** ✓ |
| Leituras p95 | < 800 ms | **1,31 s** ✗ |
| Containers | nenhum reinício | **0 reinícios**, API `healthy` o tempo todo ✓ |

- Vazão: 124 chamadas/s. Memória da API estável em ~350 MiB (antes passava de 1,1 GB). Swap
  parado em ~970 MB.
- **CPU no limite:** a VPS tem **2 núcleos** e o load average chegou a **11,6**. Médias: API 42%,
  site (Next) 53%, banco 45%. Toda chamada `/backend/*` passa pelo Next, que gasta tanto quanto a API.
- **Logins:** 44 de 219 receberam o 503 do `ScryptGate` na subida (todos os usuários virtuais entram
  juntos). Esperado: antes, esse pico derrubava a API.

**Tempo no servidor por chamada** (log da API; o p95 do k6 soma rede, Next e fila de CPU):

| Chamada | Chamadas | Mediana | p95 |
| --- | --- | --- | --- |
| `GET /catalog` (tela da loja, a cada 8 s) | 6.433 | 162 ms | **1.108 ms** |
| `GET /catalog/meta` (cliente, a cada 8 s) | 8.185 | 83 ms | **890 ms** |
| `GET /orders` | 14.610 | 29 ms | 276 ms |
| demais leituras (`/zones`, `/me/permissions`, `/catalog/search`, `/addresses`, chat, sino…) | — | 3–13 ms | 55–122 ms |

**Gargalo:** confirma os suspeitos 4 e 5 do plano. `/catalog/meta` devolve **todas** as lojas,
categorias e cobertura a cada 8 s para cada cliente logado, e `/catalog` devolve o catálogo inteiro
para cada tela de loja a cada 8 s. São as duas leituras pesadas e as que mais disputam CPU.
Caminho: a etapa 1 do **S18** (`SUGESTOES_MELHORIA_2026-11-09.md`) — tirar `/catalog/meta` e
`/zones` da consulta automática do cliente e espaçar ou condicionar o `/catalog` da tela da loja.
Depois, repetir o nível 1 antes do nível 2.

**Implementado** na PR #129 (S18 etapa 1), com o k6 ajustado ao ritmo novo.

## Terceira rodada — depois da PR #129 (`8846cb6`, 21:50–22:08 UTC)

Backup `foodie_platform-20261111-214514.sql` antes. Nível 0 passou (205 chamadas, antes 352;
0% de erros).

**Nível 1: passou em todas as metas.**

| Medida | Meta | 2ª rodada | **3ª rodada** |
| --- | --- | --- | --- |
| Erros | < 1% | 0,04% | **0,05%** ✓ |
| Leituras p95 | < 800 ms | 1,31 s | **592 ms** ✓ |
| Páginas p95 | < 1,5 s | 1,19 s | **546 ms** ✓ |
| Fechar o pedido p95 | < 2 s | 1,02 s | **338 ms** ✓ (84 pedidos) |
| Chamadas no total | — | 146.059 | **102.807** (−30%) |
| Reinícios da API | nenhum | 0 | **0** (`healthy` nas 44 amostras) ✓ |

- **VPS:** load average máximo **5,7** (antes 11,6). CPU média: API 30%, site 43%, banco 24%
  (antes 42%, 53% e 45%). Memória da API ~360 MiB. Swap caiu para 866 MB.
- **Logins:** 60 de 235 receberam o 503 do `ScryptGate`, todos na subida, quando o k6 faz
  entregadores e telas de loja entrarem juntos. Pessoas reais entram espalhadas; mesmo assim, vale
  o site tentar de novo sozinho depois de alguns segundos ao receber esse 503 `(a decidir)`.

**Conferido no navegador** (painel da loja no staging, 22:1x UTC, pelas chamadas registradas na
aba): logo depois do login vai o pacote completo (`/catalog`, `/zones`, `/me/permissions`,
`/restaurant/modules`, `/orders`, `/restaurant/couriers`); depois, a cada **8 s**, só `/orders` e
`/restaurant/couriers` (aos 9, 17, 25 e 33 s). O sino segue a cada 15 s. Pedido novo continua
chegando em até 8 s. A volta do lento aos 3 min não foi esperada no navegador (coberta pelo
`poll-plan.test.ts`).

## Nível 2 — folga 3× (`8846cb6`, 22:18–22:39 UTC): reprovou, sem cair

Antes: removidas as imagens `pre-*` de três deploys (fica só a `pre-8846cb6`, para voltar); disco
de 67% para 61%.

| Medida | Meta | Resultado |
| --- | --- | --- |
| Erros | < 1% | **0,62%** ✓ (1.310 de 210.621; 970 são o 503 do `ScryptGate`) |
| Leituras p95 | < 800 ms | **11,7 s** ✗ |
| Páginas p95 | < 1,5 s | **12,2 s** ✗ |
| Fechar o pedido p95 | < 2 s | **11,4 s** ✗ (249 pedidos criados) |
| Reinícios da API | nenhum | **0** (`healthy` nas 48 amostras) ✓ |

- **A API não é o gargalo.** No log da API, todas as chamadas ficaram rápidas: `/orders` p95
  221 ms, as demais entre 30 e 70 ms. Os 11–12 s que o k6 vê acontecem **antes** de a chamada
  chegar à API.
- **O site (Next) é.** Toda chamada `/backend/*` passa pelo Next, que só a repassa para a API
  (`rewrites` em `apps/web/next.config`). O Next roda num processo Node só e ficou em 65–80% de CPU
  o platô inteiro, com a VPS de 2 núcleos em load average de até **16,3**. As páginas também saem
  dele, por isso páginas, leituras e fechamento têm o mesmo p95.
- **Logins:** 970 recusas com 503, quase todas na subida (até 263 por minuto às 22:19), quando o
  k6 faz 660 usuários entrarem em 5 minutos. No platô, 7 a 13 logins por minuto, todos aceitos.
- O StoryVerse, na mesma VPS, não passou de 2% de CPU.

**Correção proposta (a decidir):** o Caddy mandar `/backend/*` **direto para a API**, sem passar
pelo Next (no `edge` do staging e no `deploy/Caddyfile` da produção). O Next deixa de gastar CPU
repassando ~160 chamadas/s e fica só com as páginas. Depois, repetir o nível 2. Se não bastar, o
próximo passo é uma VPS com mais núcleos.

**Aplicado no staging às 22:5x UTC** (edge, backup `Caddyfile.bak-2026-11-11-backend-direto`) e no
`deploy/Caddyfile` da produção. Conferido: os 11 sites do edge (Foodie, StoryVerse, QA, Umami)
responderam como antes; `/backend/*` responde com o `X-Request-Id` da API, sem os cabeçalhos do Next;
a sessão da loja no navegador seguiu válida; a checagem de `Origin` segue (403 para origem estranha).

**Achado de segurança no caminho:** antes da mudança, `/backend/actuator`,
`/backend/actuator/prometheus` e `/backend/v3/api-docs` respondiam **200 por fora**: o Next
repassava qualquer caminho para a API, e o bloqueio só existia no subdomínio `api.`. A regra nova
devolve 404 para esses caminhos.

## Nível 2, segunda rodada — `/backend` direto (22:46–23:07 UTC): melhorou muito, ainda reprova

| Medida | Meta | 1ª rodada | **2ª rodada** |
| --- | --- | --- | --- |
| Erros | < 1% | 0,62% | **0,46%** ✓ |
| Leituras p95 | < 800 ms | 11,7 s | **1,71 s** ✗ |
| Páginas p95 | < 1,5 s | 12,2 s | **2,49 s** ✗ |
| Fechar o pedido p95 | < 2 s | 11,4 s | **2,21 s** ✗ (246 pedidos) |
| Vazão | — | 163 chamadas/s | **220 chamadas/s** |
| Reinícios da API | nenhum | 0 | **0** ✓ |

- **Agora o limite é a CPU da máquina, não um componente.** Load average de 14 a 24 no platô inteiro
  (2 núcleos). Somando os containers: API ~50%, banco ~50–60%, site ~40%, Caddy ~30% — perto dos
  200% que a VPS tem. O Next caiu de 65–80% para ~40%.
- Os logins (1.038 recusas com 503) pesam só até 22:56; depois disso não há login, e o load segue
  entre 14 e 18. Ou seja, mesmo sem o SCrypt a VPS fica no limite com 3× a carga esperada.
- O banco subiu ao longo da rodada (de ~40% para ~60%), conforme os pedidos se acumulam: o
  `/orders` é o próximo candidato a otimizar `(a confirmar com o log de consultas lentas)`.
- O StoryVerse não passou de 3% de CPU.

**Leitura:** com 2 núcleos, a VPS aguenta a carga esperada (nível 1 passou com folga) e uns
2× sem sustos, mas não os 3× que o plano pede para o lançamento. Caminhos: **VPS com 4 núcleos**
(o mais direto; custo e plano da Hostinger `(a confirmar)`) ou seguir otimizando (`/orders`, cache
das páginas no Next, compressão no Caddy) e medir de novo. O k6 também exagera a subida: refaz o
login a cada recusa; espalhar as entradas deixa a rodada mais fiel.

## Otimizações para o nível 2 (branch `perf/otimizacoes-nivel2`, decisão de 11/11: otimizar antes de trocar de VPS)

1. **Índice dos pedidos da loja (`V081`).** `ANALYZE` no staging: a lista de pedidos encerrados da
   loja (`WHERE restaurant_id = ? AND status IN (...) ORDER BY id DESC LIMIT 100`, em toda consulta
   automática da tela da loja) varria a tabela inteira pela chave primária — **10.269 linhas lidas
   para achar 100** —, e o custo crescia com os pedidos de todas as lojas. As listas de cliente e de
   entregador já usavam os índices delas. Novo índice `ix_orders_restaurant_recent (restaurant_id, id)`;
   `OrderListIndexIntegrationTest` (MariaDB real) falhava antes (`key=PRIMARY`) e passa depois.
2. **Página pública da loja guardada por 60 s** (`/restaurantes/[id]`, ISR). Antes era montada a cada
   visita, com uma chamada à API. Conferido no site local contra a API de teste: primeira visita
   `MISS`, depois `HIT` com `s-maxage=60`; loja inexistente e id inválido seguem em 404.
3. **k6 mais fiel na entrada:** a primeira entrada de cada usuário virtual espera até 60 s, e um 503
   do `ScryptGate` é tentado de novo depois de 5 a 15 s (até 3 vezes), contado em `logins_adiados_503`.

Ficou de fora: a compressão do Caddy (sem evidência de que pese; o `/orders` de 112 KB vira 3,8 KB).
Depois do deploy: conferir o plano no staging com `ANALYZE` e refazer o nível 2.

## Nível 2, terceira rodada — depois da PR #131 (`c70ab75`, 12/11, 15:36–15:58 UTC): ainda reprova

Antes: deploy conferido (`.deployed` em `c70ab75`, schema `081`), backup
`foodie_platform-20261112-153235.sql`, disco em 51%. `ANALYZE` no staging: a lista de pedidos
encerrados da loja passou a usar o `ix_orders_restaurant_recent` e lê **142 linhas** para devolver
100 (antes 10.269). Nível 0 passou (208 chamadas, 0% de erros).

| Medida | Meta | 2ª rodada | **3ª rodada** |
| --- | --- | --- | --- |
| Erros | < 1% | 0,46% | **0,35%** ✓ (1.045 de 291.801) |
| Leituras p95 | < 800 ms | 1,71 s | **1,70 s** ✗ |
| Páginas p95 | < 1,5 s | 2,49 s | **1,30 s** ✓ |
| Fechar o pedido p95 | < 2 s | 2,21 s | **2,02 s** ✗ (255 pedidos) |
| Vazão | — | 220 chamadas/s | **225 chamadas/s** |
| Reinícios da API | nenhum | 0 | **0** (`healthy` nas 46 amostras) ✓ |

- **As páginas passaram** (cache de 60 s da vitrine); as leituras não saíram do lugar. A mediana
  das leituras é 306 ms: a cauda é que fura a meta.
- **A CPU da máquina segue no limite.** Load average de 18 a 27 no platô (2 núcleos). Médias no
  platô: API 53%, banco 43% (antes 50–60%), site 38%, Caddy 35% — cerca de 170% dos 200%. O índice
  aliviou o banco, mas a folga foi ocupada pelo resto.
- **Logins:** com a entrada espalhada e a nova tentativa, 648 logins foram adiados por um 503 e
  entraram depois; 81 de 606 desistiram após 3 tentativas (antes, 1.038 recusas). Os 503 continuaram
  até 15:44, já dentro do platô.
- **Achado novo: faltou conexão com o banco.** Três chamadas falharam com
  `CannotGetJdbcConnectionException` (15:44:31 e 15:50:04): o pool de 8 conexões (suspeito 1 do
  plano) esgotou e a espera estourou. Explica a chamada mais lenta da rodada (34 s). Quantas chamadas
  esperaram conexão sem estourar **(a confirmar** com as métricas do Hikari no Prometheus).
- Memória da API ~450 MiB; swap de 828 para 938 MB. O StoryVerse (produção) ficou em 0–1% de CPU; o
  QA dele teve picos de até 47%.

**Leitura:** as otimizações baratas acabaram sem fechar os 3×. A VPS de 2 núcleos passa o nível 1 com
folga e sustenta ~225 chamadas/s sem cair, mas com cauda lenta. Caminhos `(a decidir)`: **VPS com
4 núcleos** (o mais direto) ou mais uma rodada de otimização — medir a espera no pool e decidir o
tamanho dele, e reduzir o custo do `/orders` da tela da loja (a lista em andamento lê os ~540 pedidos
da loja a cada 8 s).

### Espera no pool de conexões (Prometheus, platô de 15:43 a 15:57 UTC)

| Medida | Valor |
| --- | --- |
| Chamadas na API | 215 por segundo |
| Conexões pedidas ao pool | 857 por segundo — **~4 por chamada** |
| Espera média por conexão | **45 ms** (picos de 120 ms por minuto; máxima de 5 s, o `connection-timeout`) |
| Uso médio de cada conexão | 6 ms |
| Conexões ocupadas, em média | 5,5 de 8 |
| Chamadas na fila do pool (amostras a cada 60 s) | 0 em metade das amostras; **34 a 116** nas outras |

- **A fila do pool é a maior parte do tempo da API.** 857 pedidos de conexão × 45 ms dão ~38 s de
  espera por segundo de relógio; a soma do tempo de todas as chamadas da API no mesmo intervalo é
  ~42–48 s por segundo. Ou seja, cerca de 80% do tempo que a API leva para responder é fila por
  conexão, não consulta.
- Aparece nas chamadas triviais: `/orders/chat/unread` levou 200 ms em média e `/catalog/tags`,
  133 ms (na 1ª rodada do nível 2, o p95 dessas ficava entre 30 e 70 ms).
- O banco não está no limite (43% de CPU, 6 ms por uso de conexão). As conexões ficam presas com
  threads da API que esperam CPU (load de 18 a 27), e 8 não bastam para as rajadas.
- Cada chamada pede ~4 conexões porque cada consulta fora de transação pega e devolve a sua
  (sessão, permissões e as consultas da rota).

**Correção (branch `perf/pool-conexoes`, decisão de 12/11):** `maximum-pool-size` de 8 para 24 no
`application.yml`. Conferido no staging: `max_connections = 151` e pico histórico de 11 conexões.
Falta publicar e repetir o nível 2 **(a confirmar)**. Não garante a meta: a CPU da
VPS continua no limite, e mais consultas ao mesmo tempo disputam os mesmos 2 núcleos. Segundo passo,
se precisar: reduzir as conexões por chamada (a consulta da sessão em toda chamada).

**Próximo (depois do nível 2 passar):** nível 4 (resistência, 60 min). O nível 3 (ruptura) só
depois de combinar, porque a VPS é compartilhada com o StoryVerse.
