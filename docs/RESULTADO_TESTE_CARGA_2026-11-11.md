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
