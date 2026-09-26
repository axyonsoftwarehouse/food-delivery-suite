# Avaliação técnica e plano de evolução

Data: 24/09/2026. Escopo: código e configuração do workspace, com foco na plataforma independente definida em `PLANO_RECONSTRUCAO_PROPRIA.md`.

## Diagnóstico executivo

O projeto tem uma base funcional de demonstração, mas ainda não reúne as condições para uma operação comercial completa. A prioridade é concluir a operação e torná-la verificável: tratamento de imprevistos dos pedidos, segurança de contas, pagamentos, avisos, administração e recuperação de falhas.

A plataforma própria já tem autenticação por sessão, quatro papéis, catálogo, cobertura por CEP, endereço, carrinho persistente, checkout transacional, autorização por vínculo do pedido e histórico de estados. Essas bases devem ser preservadas. Não é necessário começar outra reconstrução nem introduzir microsserviços agora.

Há duas pilhas que precisam ser tratadas separadamente:

| Base | Situação observada | Direção |
| --- | --- | --- |
| `platform/apps/web` + `platform/apps/api-java` | Next.js/TypeScript e Java 21/Spring Boot; ciclo demonstrativo implementado | Concentrar aqui as funcionalidades novas |
| `platform/apps/api` | API TypeScript transitória; ainda dona das migrations e seed | Retirar somente após transferência do schema e validação de contratos |
| `admin-panel`, `web`, `app-*`, `payment-gateway` | Laravel, Next anterior e Flutter; contratos distintos da nova plataforma | Preservar como legado e referência; integração não acontece apenas trocando a URL |
| `deploy/` | Composição de publicação do legado | Criar publicação independente para a plataforma própria |

Os registros anteriores de HTTP 503 por ativação estão em `AUDITORIA_IMPEDIMENTOS.md`. As rotas locais continuam usando esses middlewares, mas a produção **não foi consultada novamente nesta avaliação**. Os documentos antigos misturam achados históricos e correções posteriores; não devem ser lidos como um retrato atual de cada rota.

## Evidência e limites

- Inspecionados: fluxos Java de autenticação, catálogo, CEP, carrinho e pedidos; administração; telas principais Next; schema e migrations; scripts e testes; composição de deploy; configuração e rotas selecionadas do legado.
- Java: **18 testes aprovados**, com `mvn -o test`, usando `C:\Users\werne\tools\jdk-21` como `JAVA_HOME` nesta execução.
- TypeScript: **3 testes de domínio aprovados**, com `node --import tsx --test --test-isolation=none src/domain.test.ts` em `platform/apps/api`.
- Tipagem: aprovada na API TypeScript e no novo web, chamando diretamente `node node_modules/typescript/bin/tsc --noEmit`; no web foi usado também `--incremental false`.
- O comando padrão de testes Node encontrou `spawn EPERM` no ambiente restrito; a execução sem isolamento passou. `pnpm ... exec tsc` não encontrou o executável, mas a chamada direta ao compilador instalado passou. Isso é uma limitação da execução/configuração local, não evidência de erro de tipos.
- O Java padrão do terminal é 8; há JDK 21 instalado e os testes passaram após selecioná-lo explicitamente. Documentar essa seleção para evitar falhas no onboarding.
- Em 24/09/2026, após iniciar o Docker Desktop, o MariaDB 11.4 local ficou saudável e a API Java reconstruída em contêiner respondeu `GET /health` com sucesso. A migration foi aplicada e sua repetição terminou sem reaplicar passos; o seed demonstrativo terminou com sucesso. Contra essa API na porta 4001 e esse banco, passaram o smoke de fluxo completo de pedido (pedido #15) e o smoke de carrinho (isolamento entre clientes, incrementos simultâneos e checkout; pedido #17). A primeira tentativa do smoke de pedido ocorreu antes da API terminar o boot e falhou por conexão encerrada; após a verificação explícita de readiness, passou. Esses resultados validam o caminho demonstrativo integrado, não os cenários de erro, lock e recuperação listados na matriz. O `docker compose config` foi renderizado com sucesso; ele contém somente o MariaDB por padrão e a API Java no perfil `java`.
- O build de produção do Next foi tentado com `pnpm --filter @foodie/web build`: a compilação Turbopack foi concluída, mas a etapa posterior de TypeScript falhou com `spawn EPERM` no ambiente atual. A checagem direta `node node_modules/typescript/bin/tsc --noEmit --incremental false` passou. Assim, a compilação foi observada, porém o build de produção completo continua **não aprovado** até ser repetido em um ambiente que permita criar o processo filho.
- A interface Next foi iniciada com a API Java como destino padrão e a tela de acesso foi carregada no navegador local, incluindo os campos de email e senha. Não foi executada nesta passagem uma jornada manual completa dos quatro papéis; os smokes acima são a evidência integrada do fluxo de pedidos.
- A suíte Java inspecionada contém testes de domínio e controllers com dependências simuladas; seu sucesso não comprova SQL, locks, rollback ou concorrência em MariaDB.
- Não é auditoria exaustiva do legado, pentest, avaliação jurídica ou varredura atualizada de vulnerabilidades de dependências.
- A árvore de trabalho já contém uma reorganização grande e arquivos novos. Esta avaliação acrescenta apenas este documento; não reorganiza nem confirma essas mudanças em commit.

## Problemas e riscos priorizados

P0 = condição para liberar operação pública/comercial; P1 = corrigir antes do piloto operacional; P2 = evolução e sustentabilidade. “Confirmado no código” descreve o comportamento implementado, não um incidente observado na produção.

### A01 — Contas sem proteção suficiente contra abuso — P0

**Confirmado na implementação Java:** login e signup estão expostos sem limitador de tentativas na aplicação inspecionada. Cadastro não confirma email; não há recuperação de senha, bloqueio de usuário ou gestão de sessões por dispositivo. `AuthService` cria sessões de sete dias e `AuthRepository` só remove a sessão do logout. O cookie tem HttpOnly e SameSite=Lax, o que é positivo; Secure depende de configuração e vem desativado por padrão local.

**Evidências:** `platform/apps/api-java/src/main/java/com/foodie/api/auth/AuthController.java`, `AuthService.java`, `AuthRepository.java`, `platform/apps/api-java/src/main/resources/application.yml`.

**Entrega:** limitar login/cadastro por origem e conta, respostas adequadas de limitação, verificação de email, recuperação com token de uso único, revogação de sessões e conta suspensa, limpeza de sessões expiradas e configuração HTTPS validada. Revisar origem/CSRF das operações autenticadas no desenho final de domínios.

**Aceite:** excesso de tentativas é contido; tokens usados/expirados são rejeitados; conta suspensa perde acesso; cookies de produção têm Secure; nenhum segredo aparece em logs. Confirmar também as proteções do proxy na homologação.

### A02 — Pedidos não têm saídas para imprevistos — P0

**Confirmado:** `OrderWorkflow.java:13–17` só permite aceitar, marcar pronto, atribuir, retirar e entregar. O enum de `platform/apps/api/schema.sql` acompanha esse caminho. Não há recusa, cancelamento, expiração, falha de entrega ou reatribuição de pedido já atribuído.

**Impacto:** restaurante sem condições de preparar e entregador indisponível deixam pedidos sem resolução suportada pelo sistema.

**Entrega:** matriz de estados e permissões com motivo, autor e horário; definir janelas de cancelamento, recusa, reatribuição e conclusão excepcional. Manter estado financeiro separado do estado logístico.

**Aceite:** cenários de recusa, cancelamento antes/depois do aceite, troca de entregador e falha de entrega chegam a um resultado auditável; transições concorrentes não geram eventos contraditórios.

### A03 — Checkout não valida a versão/composição confirmada — P1

**Risco derivado do código, sem reprodução em banco nesta sessão:** `CartService.java:78–92` lê o carrinho atual e compara somente `expectedTotalCents`. Se outra sessão trocar um item por outro do mesmo valor, o total continua igual e a compra pode conter itens diferentes dos que a primeira sessão exibiu.

Além disso, `OrderController.java` mantém `POST /orders`, cujo contrato recebe itens e endereço, mas não total esperado nem chave de idempotência. Repetir essa requisição pode criar dois pedidos. O checkout do carrinho tem lock e limpeza transacional, o que já evita que duas chamadas simplesmente consumam o mesmo carrinho; ainda falta devolver o mesmo resultado após perda de resposta/reenvio.

**Entrega:** versão ou identificador da cotação/carrinho, validação de composição e preços, chave de idempotência por cliente e operação. Unificar as garantias de todos os caminhos de criação de pedido ou retirar o endpoint transitório após verificar consumidores.

**Aceite:** troca de item pelo mesmo preço exige nova confirmação; retry da mesma operação devolve o mesmo pedido; teste de resposta perdida e chamadas simultâneas não gera duplicação.

### A04 — Operação bem-sucedida pode aparecer como erro — P1

**Confirmado no fluxo:** `platform/apps/web/app/page.tsx:103` coloca `await action()` e `await refresh(user)` no mesmo try/catch. Se o POST concluir e uma consulta posterior falhar, a função retorna false e mostra erro. `refresh` agrega várias consultas em `Promise.all` antes de atualizar a interface.

**Impacto:** cliente ou administrador pode tentar novamente algo que já ocorreu; uma falha auxiliar impede atualizar os demais dados. No login/logout, o estado visual também depende do carregamento agregado.

**Entrega:** distinguir gravação concluída de atualização da tela; usar o resultado da operação, tratar falhas por seção e oferecer recarga sem repetir a mutação. Tratar sessão expirada e falhas não JSON do proxy com mensagens compreensíveis.

**Aceite:** simular POST 201 seguido de GET 503; a UI confirma o pedido/cadastro e oferece somente recarregar os dados, sem sugerir nova criação.

### A05 — Formulários perdem dados quando a API falha — P1

**Confirmado:** em `platform/apps/web/app/page.tsx:165–172`, os formulários de restaurante, categoria, produto, responsável e zona chamam `run(...)` e limpam campos imediatamente, sem aguardar sucesso. O formulário de faixa de CEP já usa o padrão correto de aguardar `ok`.

**Entrega e aceite:** aplicar o padrão de sucesso aos demais formulários. Resposta 400, 409 ou 503 deve preservar os dados relevantes, manter feedback de erro e permitir correção/reenvio. Tratar campos de senha conforme a política de segurança definida.

### A06 — Histórico e fila param nos 100 pedidos mais novos — P1

**Confirmado:** `OrderService.java:102–105` aplica `LIMIT 100` para todos os papéis, sem cursor nem filtros. “Ver todos” no cliente só expande a lista já recebida.

**Impacto:** pedidos antigos deixam de ser acessíveis pela listagem; um pedido ainda aberto pode sumir da fila após novos pedidos. Os dados não são apagados do banco.

**Entrega:** separar fila ativa de histórico; paginação estável, filtros por estado/data e busca por ID; índices após verificar plano de consulta.

**Aceite:** com mais de 100 pedidos e um pedido antigo aberto, a fila continua exibindo o aberto e o histórico permite navegar por todos sem duplicações/lacunas.

### A07 — Administração não sustenta operação independente — P0 para piloto

**Confirmado no conjunto de controllers/tela inspecionado:** existe criação de restaurantes, categorias, produtos e usuários de restaurante; entregadores apenas são listados em `/admin/couriers`. Não há fluxo de cadastro/aprovação/suspensão de entregador. Também faltam edição completa de catálogo e regras comerciais, horários/fechamento do restaurante e gestão de acessos.

**Impacto:** a demonstração depende de seed/intervenção técnica para completar cadastros e corrigir dados operacionais. Restaurante ativo não equivale a restaurante aberto naquele horário.

**Entrega e aceite:** administrador monta uma operação do zero sem SQL/seed: cadastra acessos e entregadores, configura cobertura, edita preços e horários, pausa lojas e produtos. Checkout respeita fechamento e disponibilidade.

### A08 — Pagamentos e conciliação ainda não existem na nova base — P0 para venda online

**Lacuna confirmada:** schema e serviços novos não implementam cobrança, webhooks, estornos ou conciliação; o README descreve pedidos demonstrativos. A pasta `payment-gateway` do legado não é uma integração pronta para Java.

**Entrega:** decidir modalidades do piloto. Para pagamento na entrega, registrar método, valor devido e confirmação de recebimento. Para pagamento online, integrar sandbox do provedor escolhido, validar callbacks, impedir processamento duplicado, tratar expiração e reembolso e reconciliar valores. Definir comissão, frete e repasse com responsáveis do negócio antes de automatizá-los.

**Aceite:** pagamento aprovado/recusado/expirado, callback repetido e fora de ordem, divergência de valor e estorno têm testes; nunca confiar apenas na tela de retorno do cliente para confirmar pagamento. Não há escolha de provedor nem orçamento aprovado neste plano.

### A09 — Operação depende de atualização manual — P1

**Confirmado na UI:** pedidos são carregados na entrada, após ações ou pelo botão de atualização. Não há polling periódico, stream ou notificações no fluxo novo analisado.

**Impacto:** restaurante pode não perceber novo pedido; cliente vê estado antigo até atualizar; não existe alerta de atraso.

**Entrega:** atualização automática com reconexão e fallback, aviso de novo pedido e fila atrasada; notificações externas com fila, retentativas e deduplicação quando forem integradas.

**Aceite:** um pedido criado em outra sessão aparece dentro de um prazo acordado (sugestão inicial: até 10 segundos no piloto); perda de conexão fica visível e reconexão recupera o estado correto.

### A10 — Migração de banco não tem recuperação robusta — P1

**Risco concreto do desenho:** `platform/apps/api/src/migrate.ts` executa cada instrução e só depois grava o nome da migration. `005_postal_coverage.sql`, por exemplo, cria uma tabela e depois altera endereços. Se houver interrupção entre os passos, a repetição tenta criar a tabela novamente. Não há checksum nem bloqueio explícito de dois migradores. A API Java depende desse schema, mas seu contêiner não o prepara.

**Entrega:** transferir migrations para um único proprietário Java, com baseline verificado dos bancos existentes, lock, checksums e procedimento de recuperação de DDL parcial. Não executar dois sistemas de migration em paralelo. Fazer backup/restauração antes de aplicar em base relevante.

**Aceite:** banco vazio e cópia de banco existente chegam ao mesmo schema; nova execução não reaplica passos; falha simulada tem recuperação documentada; alteração retroativa de migration é detectada.

### A11 — Testes e publicação não validam o produto completo — P1

**Confirmado:** `platform/package.json` executa somente testes TypeScript em `test`; `build` recursivo não inclui Maven, pois Java não é pacote pnpm. Não foi encontrada pasta `.github` no workspace. O Dockerfile Java usa `-DskipTests`. Existem smokes úteis, mas não testes Java com MariaDB real na suíte inspecionada.

**Entrega:** um comando/pipeline de verificação para Java, tipos, builds, contratos, migrations e E2E dos quatro papéis. Banco efêmero por execução, separado de demonstração e produção. Fixar versões de ferramentas e instalação pelo lockfile; substituir `latest` dos manifests por faixas deliberadas, sem perder o lock atual.

**Aceite:** clone limpo chega a um ambiente de teste reproduzível; falha em qualquer componente bloqueia publicação; smokes não criam dados em banco real por engano. O uso de `latest` é risco de atualização não controlada, não prova de vulnerabilidade.

### A12 — Publicação e recuperação da plataforma própria estão incompletas — P0 para produção

**Confirmado na configuração versionada e na VPS:** `deploy/docker-compose.yml` publica o legado. Foi adicionada uma composição de homologação independente em `platform/deploy/`, nomeada `foodie-staging`, com MariaDB isolado, etapa de migration, API Java, web Next e Caddy opcional. Em Docker local e na VPS, a migration concluiu, MariaDB e API ficaram saudáveis e o web respondeu HTTP 200 pela rede interna. Na VPS, a primeira subida não abriu portas públicas nem reutilizou recursos do legado. A homologação pública está disponível em HTTPS por um host `sslip.io`, com certificado Let's Encrypt válido; enquanto o legado ocupar 80/443, a rota passa pelo Caddy já ativo, sem troca do domínio principal. Isso não constitui aprovação para produção. `/health` verifica `SELECT 1`, mas não a versão do schema. Não há rotina de backup/restauração e pipeline da nova pilha nesses arquivos. Isso não prova ausência de backups externos na VPS.

**Entrega:** homologação separada, imagens verificadas, configuração explícita do proxy Next para a API na rede de contêineres, migrations como etapa controlada, readiness, logs com correlação, métricas, alertas, backup externo e ensaio de restauração/rollback. Validar o momento em que `API_INTERNAL_URL` é aplicado na construção/publicação do Next.

**Aceite:** ambiente sobe a partir de documentação; aplicação só recebe tráfego com schema correto; queda de banco é detectada; restauração e retorno à versão anterior são demonstrados com dados de teste.

## Planejamento por entregas

Não há informação suficiente de equipe, carga de trabalho ou prazo comercial para prometer datas. As etapas abaixo são marcos de aceite; estimar calendário depois de decompor as duas primeiras. Backend, frontend, QA e operação são responsabilidades, não pressupõem quatro pessoas contratadas.

| Etapa | Entregas | Responsáveis funcionais | Dependência e conclusão |
| --- | --- | --- | --- |
| 1. Base verificável | Ambiente JDK/Node reproduzível; comando de verificação; MariaDB de testes; contratos atuais; documentação de legado vs novo | Backend + QA/operação | Entrada para todas as demais; fluxo atual passa em ambiente limpo |
| 2. Correções de consistência | A03–A06; versão do carrinho; idempotência; separar gravação/recarga; formulários; paginação; migrations confiáveis | Backend + frontend + QA | Depende de 1; cenários de falha e concorrência passam |
| 3. Operação real mínima | A02, A07, A09; cadastros completos; horários; recusa/cancelamento; entregadores; reatribuição; atualização automática | Produto + backend + frontend | Depende de 2; equipe executa jornada e exceções sem editar banco |
| 4. Contas e dinheiro | A01 e A08; recuperação/verificação de conta; proteção contra abuso; método de pagamento; conciliação e reembolso | Backend + frontend + responsável financeiro | Proteção de contas pode começar na etapa 2; piloto público depende deste aceite |
| 5. Homologação e piloto controlado | A11–A12; testes de carga; alertas; backup e rollback; ensaio de migração; treinamento e suporte | QA + operação + produto | Depende de 1–4; escopo pequeno de restaurantes/zonas com acompanhamento |
| 6. Expansão do produto | Melhorias de experiência e comerciais, aplicativos próprios e automação logística | Produto + engenharia | Depois de medir estabilidade e demanda no piloto |

A sequência inicial de trabalho recomendada é: preparar testes com banco → corrigir confirmação/idempotência do checkout e feedback da UI → corrigir formulários e fila → fechar estados excepcionais → completar cadastros operacionais. Segurança deve avançar junto e bloquear exposição pública até o aceite.

## Escopo para um produto mais completo

| Área | Essencial para o piloto | Evolução depois da base estável |
| --- | --- | --- |
| Cliente | Cadastro/recuperação, escolha de endereço, catálogo por cobertura, carrinho confiável, pedido e pagamento, acompanhamento e suporte | Favoritos, recompra, avaliações moderadas, cupons e fidelidade |
| Cardápio | Edição de produto/preço/descrição/imagem, disponibilidade, horários; complementos quando necessários ao cardápio piloto | Combos, adicionais com regras mínimas/máximas, variantes, estoque por período, agendamento |
| Restaurante | Fila ativa, aceite/recusa, prazo de preparo, pausa da loja, impressão se necessária e histórico | Equipe com permissões, relatórios, múltiplas unidades, integração com cozinha/PDV |
| Entregador | Cadastro e aprovação, disponibilidade, atribuição, retirada, conclusão e ocorrência | Oferta/aceite de corridas, prova de entrega, localização consentida, navegação, histórico de ganhos |
| Administração | Gestão de acessos, zonas, lojas, entregadores, pedidos, cancelamentos e trilha administrativa | Comissões, repasses, relatórios de desempenho e regras comerciais por estabelecimento |
| Endereços/logística | Cobertura real cadastrada e validada, complemento/referência, taxa clara e endereço preservado no pedido | Geocodificação, perímetros, cálculo de distância, prazo estimado e despacho automático |
| Financeiro | Situação do recebimento, estorno e conciliação conforme modalidade escolhida | Extratos, repasses auditáveis, exportação contábil e políticas comerciais avançadas |
| Experiência | Português consistente, celular, teclado, foco, contraste, estados vazios/carregamento/erro, sessão expirada | Pesquisa com usuários, otimização de conversão e personalização |
| Suporte e dados | Canal de ajuda, tratamento de ocorrências, inventário de dados, retenção e controle de acesso definidos | Ferramentas de atendimento, solicitações de exportação/exclusão e métricas operacionais |
| Mobile | Web responsivo validado nos dispositivos do piloto | Apps próprios após contratos estabilizados; adaptar consumidores Flutter exige projeto específico |

Cobertura por CEP não confirma existência do endereço e não calcula distância. No MVP isso pode ser uma regra válida, desde que a área cadastrada corresponda ao atendimento e a operação tenha procedimento para endereço incorreto. Não apresentar taxa fixa como cálculo real de rota.

## Matriz mínima de validação antes do piloto

1. Cliente A não lê nem altera carrinho, endereço ou pedido de B; restaurante e entregador acessam apenas pedidos autorizados.
2. CEP inválido/descoberto, mudança de faixa, loja fechada, produto pausado e valor mínimo impedem checkout adequadamente.
3. Duas sessões alterando carrinho; composição muda com total igual; preço muda; tentativa concorrente; resposta perdida e retry.
4. Pedido transita pelo caminho normal e por recusa, cancelamento, expiração, troca de entregador e falha de entrega com histórico correto.
5. Mais de 100 pedidos, pedidos abertos antigos e navegação do histórico.
6. Pagamento conforme modalidade: falha, expiração, repetição de callback, valores divergentes e reembolso quando aplicável.
7. API fora do ar, resposta HTML do proxy, timeout e GET com falha depois de POST concluído; usuário não perde dados nem é induzido a duplicar ação.
8. Banco vazio, atualização de schema existente, interrupção de migration, backup e restauração; nenhum teste destrutivo na produção.
9. Quatro papéis em navegadores/sessões distintos; comportamento móvel, teclado, reconexão e expiração de sessão.
10. Carga compatível com a quantidade acordada de lojas/pedidos, acompanhando latência, erros, pool de conexões e fila; fixar metas após medição inicial.

## Critério de liberação e decisões de produto

Liberar o piloto somente quando não houver P0 aberto aplicável ao escopo, as correções P1 necessárias ao fluxo estiverem verificadas, os quatro papéis conseguirem operar sem SQL/seed, a matriz crítica passar e houver suporte, backup restaurado em teste e caminho de rollback. Não exigir todos os recursos comerciais da etapa 6 para começar a validar o negócio.

Decisões a registrar antes das integrações: modalidades de pagamento, quem recebe/repassa valores, entrega própria ou da plataforma, área inicial, horário de operação, política de recusa/cancelamento, necessidade de adicionais/combos, quantidade de restaurantes e pedidos esperada e responsável por suporte. Questões de privacidade, termos e exigências contratuais devem ser verificadas com responsáveis competentes antes da publicação; este documento não estabelece conformidade legal.

Usar este documento como backlog consolidado e manter os planos anteriores como histórico de decisões. Cada item deve receber responsável, issue, evidência de teste e estado de conclusão conforme a implementação avance.

## Adendo de reconciliação — 25/09/2026

Revisão do backlog A01–A12 contra o código atual da API Java e do web. As linhas acima
permanecem como registro histórico do estado em 24/09/2026; a tabela abaixo é o retrato
de 25/09/2026. Evidência por arquivo.

| Item | Estado atual | Evidência |
| --- | --- | --- |
| A01 Contas sem proteção | **Implementado** | `auth/AuthRepository.java` (tabela `auth_login_limits` com janela e bloqueio), `auth/AuthService.java`, `auth/SessionCleanup.java`, tokens de uso único |
| A02 Sem saídas para imprevistos | **Implementado** | `orders/OrderWorkflow.java`, `OrderService.java` (recusa, cancelamento, expiração 15 min, `assign`/`unassign`, `fail` com motivo em `order_events.reason`) |
| A03 Checkout não valida versão/composição | **Resolvido** | `CartService` calcula `version` (hash da composição/preços), valida `expectedVersion` (409) e usa `order_idempotency` (`V023`) para devolver o mesmo pedido em retry; `POST /orders` foi **retirado** e o `smoke` migrou para o carrinho |
| A04 Operação bem-sucedida aparece como erro | **Resolvido** | `web/app/app-context.tsx` mostra o sucesso da mutação e trata a recarga à parte; `CatalogManager`/`CouponsPanel` separam gravação de recarga |
| A05 Formulários perdem dados | **Resolvido** | `CatalogManager.tsx` aguarda sucesso antes de limpar; demais formulários usam o mesmo padrão |
| A06 Histórico/fila limitados a 100 | **Resolvido** | `OrderService.list` devolve **todos os pedidos ativos** + 100 terminais; `GET /orders/history` pagina por cursor (`after`/`status`/`limit`) e o cliente tem "Carregar mais histórico" |
| A07 Administração insuficiente | **Implementado** | `admin/AdminController.java` (zonas, faixas de CEP, restaurantes, categorias, produtos, entregadores com aprovação/suspensão), `hours/*` |
| A08 Pagamentos/conciliação ausentes | **Implementado** | `orders/PaymentService.java`, `payments/OnlinePaymentService.java`, `payments/MercadoPagoGateway.java`, `payments/PaymentWebhookController.java`, `GET /admin/payments` |
| A09 Operação depende de atualização manual | **Implementado** | `web/app/app-context.tsx:187-206` (polling de 8 s, pausa em aba oculta, aviso de novo pedido, atraso > 10 min) |
| A10 Migração sem recuperação | **Implementado** | Flyway é dono único das migrations em `api-java/.../db/migration`; `GET /health` traz versão do schema e `GET /ready` exige banco migrado |
| A11 Testes/publicação incompletos | **Implementado em grande parte** | `pnpm verify`, `VERIFY_INTEGRATION=1 pnpm verify`, smokes dos quatro papéis, `pnpm load` |
| A12 Publicação/recuperação incompletas | **Implementado em grande parte** | `deploy/` (`foodie-staging`), serviço `migrate` em `MIGRATE_ONLY`, `deploy/backup.sh`, `deploy/restore.sh` |

Prioridades de consistência **A03, A04, A05 e A06 resolvidas** (26/09/2026): versão do carrinho e
idempotência com a retirada de `POST /orders`, separação de gravação/recarga na UI e paginação da
fila/histórico. O fluxo completo continua validado pelo `smoke`. A análise das inspirações externas
está em `REFERENCIA_INSPIRACOES.md`.
