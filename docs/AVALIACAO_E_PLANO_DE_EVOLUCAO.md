# AvaliaÃ§Ã£o tÃ©cnica e plano de evoluÃ§Ã£o

Data: 24/09/2026. Escopo: cÃ³digo e configuraÃ§Ã£o do workspace, com foco na plataforma independente definida em `PLANO_RECONSTRUCAO_PROPRIA.md`.

## DiagnÃ³stico executivo

O projeto tem uma base funcional de demonstraÃ§Ã£o, mas ainda nÃ£o reÃºne as condiÃ§Ãµes para uma operaÃ§Ã£o comercial completa. A prioridade Ã© concluir a operaÃ§Ã£o e tornÃ¡-la verificÃ¡vel: tratamento de imprevistos dos pedidos, seguranÃ§a de contas, pagamentos, avisos, administraÃ§Ã£o e recuperaÃ§Ã£o de falhas.

A plataforma prÃ³pria jÃ¡ tem autenticaÃ§Ã£o por sessÃ£o, quatro papÃ©is, catÃ¡logo, cobertura por CEP, endereÃ§o, carrinho persistente, checkout transacional, autorizaÃ§Ã£o por vÃ­nculo do pedido e histÃ³rico de estados. Essas bases devem ser preservadas. NÃ£o Ã© necessÃ¡rio comeÃ§ar outra reconstruÃ§Ã£o nem introduzir microsserviÃ§os agora.

HÃ¡ duas pilhas que precisam ser tratadas separadamente:

| Base | SituaÃ§Ã£o observada | DireÃ§Ã£o |
| --- | --- | --- |
| `platform/apps/web` + `platform/apps/api-java` | Next.js/TypeScript e Java 21/Spring Boot; ciclo demonstrativo implementado | Concentrar aqui as funcionalidades novas |
| `platform/apps/api` | API TypeScript transitÃ³ria; ainda dona das migrations e seed | Retirar somente apÃ³s transferÃªncia do schema e validaÃ§Ã£o de contratos |
| `admin-panel`, `web`, `app-*`, `payment-gateway` | Laravel, Next anterior e Flutter; contratos distintos da nova plataforma | Preservar como legado e referÃªncia; integraÃ§Ã£o nÃ£o acontece apenas trocando a URL |
| `deploy/` | ComposiÃ§Ã£o de publicaÃ§Ã£o do legado | Criar publicaÃ§Ã£o independente para a plataforma prÃ³pria |

Os registros anteriores de HTTP 503 por ativaÃ§Ã£o estÃ£o em `AUDITORIA_IMPEDIMENTOS.md`. As rotas locais continuam usando esses middlewares, mas a produÃ§Ã£o **nÃ£o foi consultada novamente nesta avaliaÃ§Ã£o**. Os documentos antigos misturam achados histÃ³ricos e correÃ§Ãµes posteriores; nÃ£o devem ser lidos como um retrato atual de cada rota.

## EvidÃªncia e limites

- Inspecionados: fluxos Java de autenticaÃ§Ã£o, catÃ¡logo, CEP, carrinho e pedidos; administraÃ§Ã£o; telas principais Next; schema e migrations; scripts e testes; composiÃ§Ã£o de deploy; configuraÃ§Ã£o e rotas selecionadas do legado.
- Java: **18 testes aprovados**, com `mvn -o test`, usando `C:\Users\werne\tools\jdk-21` como `JAVA_HOME` nesta execuÃ§Ã£o.
- TypeScript: **3 testes de domÃ­nio aprovados**, com `node --import tsx --test --test-isolation=none src/domain.test.ts` em `platform/apps/api`.
- Tipagem: aprovada na API TypeScript e no novo web, chamando diretamente `node node_modules/typescript/bin/tsc --noEmit`; no web foi usado tambÃ©m `--incremental false`.
- O comando padrÃ£o de testes Node encontrou `spawn EPERM` no ambiente restrito; a execuÃ§Ã£o sem isolamento passou. `pnpm ... exec tsc` nÃ£o encontrou o executÃ¡vel, mas a chamada direta ao compilador instalado passou. Isso Ã© uma limitaÃ§Ã£o da execuÃ§Ã£o/configuraÃ§Ã£o local, nÃ£o evidÃªncia de erro de tipos.
- O Java padrÃ£o do terminal Ã© 8; hÃ¡ JDK 21 instalado e os testes passaram apÃ³s selecionÃ¡-lo explicitamente. Documentar essa seleÃ§Ã£o para evitar falhas no onboarding.
- Em 24/09/2026, apÃ³s iniciar o Docker Desktop, o MariaDB 11.4 local ficou saudÃ¡vel e a API Java reconstruÃ­da em contÃªiner respondeu `GET /health` com sucesso. A migration foi aplicada e sua repetiÃ§Ã£o terminou sem reaplicar passos; o seed demonstrativo terminou com sucesso. Contra essa API na porta 4001 e esse banco, passaram o smoke de fluxo completo de pedido (pedido #15) e o smoke de carrinho (isolamento entre clientes, incrementos simultÃ¢neos e checkout; pedido #17). A primeira tentativa do smoke de pedido ocorreu antes da API terminar o boot e falhou por conexÃ£o encerrada; apÃ³s a verificaÃ§Ã£o explÃ­cita de readiness, passou. Esses resultados validam o caminho demonstrativo integrado, nÃ£o os cenÃ¡rios de erro, lock e recuperaÃ§Ã£o listados na matriz. O `docker compose config` foi renderizado com sucesso; ele contÃ©m somente o MariaDB por padrÃ£o e a API Java no perfil `java`.
- O build de produÃ§Ã£o do Next foi tentado com `pnpm --filter @foodie/web build`: a compilaÃ§Ã£o Turbopack foi concluÃ­da, mas a etapa posterior de TypeScript falhou com `spawn EPERM` no ambiente atual. A checagem direta `node node_modules/typescript/bin/tsc --noEmit --incremental false` passou. Assim, a compilaÃ§Ã£o foi observada, porÃ©m o build de produÃ§Ã£o completo continua **nÃ£o aprovado** atÃ© ser repetido em um ambiente que permita criar o processo filho.
- A interface Next foi iniciada com a API Java como destino padrÃ£o e a tela de acesso foi carregada no navegador local, incluindo os campos de email e senha. NÃ£o foi executada nesta passagem uma jornada manual completa dos quatro papÃ©is; os smokes acima sÃ£o a evidÃªncia integrada do fluxo de pedidos.
- A suÃ­te Java inspecionada contÃ©m testes de domÃ­nio e controllers com dependÃªncias simuladas; seu sucesso nÃ£o comprova SQL, locks, rollback ou concorrÃªncia em MariaDB.
- NÃ£o Ã© auditoria exaustiva do legado, pentest, avaliaÃ§Ã£o jurÃ­dica ou varredura atualizada de vulnerabilidades de dependÃªncias.
- A Ã¡rvore de trabalho jÃ¡ contÃ©m uma reorganizaÃ§Ã£o grande e arquivos novos. Esta avaliaÃ§Ã£o acrescenta apenas este documento; nÃ£o reorganiza nem confirma essas mudanÃ§as em commit.

## Problemas e riscos priorizados

P0 = condiÃ§Ã£o para liberar operaÃ§Ã£o pÃºblica/comercial; P1 = corrigir antes do piloto operacional; P2 = evoluÃ§Ã£o e sustentabilidade. â€œConfirmado no cÃ³digoâ€ descreve o comportamento implementado, nÃ£o um incidente observado na produÃ§Ã£o.

### A01 â€” Contas sem proteÃ§Ã£o suficiente contra abuso â€” P0

**Confirmado na implementaÃ§Ã£o Java:** login e signup estÃ£o expostos sem limitador de tentativas na aplicaÃ§Ã£o inspecionada. Cadastro nÃ£o confirma email; nÃ£o hÃ¡ recuperaÃ§Ã£o de senha, bloqueio de usuÃ¡rio ou gestÃ£o de sessÃµes por dispositivo. `AuthService` cria sessÃµes de sete dias e `AuthRepository` sÃ³ remove a sessÃ£o do logout. O cookie tem HttpOnly e SameSite=Lax, o que Ã© positivo; Secure depende de configuraÃ§Ã£o e vem desativado por padrÃ£o local.

**EvidÃªncias:** `platform/apps/api-java/src/main/java/com/foodie/api/auth/AuthController.java`, `AuthService.java`, `AuthRepository.java`, `platform/apps/api-java/src/main/resources/application.yml`.

**Entrega:** limitar login/cadastro por origem e conta, respostas adequadas de limitaÃ§Ã£o, verificaÃ§Ã£o de email, recuperaÃ§Ã£o com token de uso Ãºnico, revogaÃ§Ã£o de sessÃµes e conta suspensa, limpeza de sessÃµes expiradas e configuraÃ§Ã£o HTTPS validada. Revisar origem/CSRF das operaÃ§Ãµes autenticadas no desenho final de domÃ­nios.

**Aceite:** excesso de tentativas Ã© contido; tokens usados/expirados sÃ£o rejeitados; conta suspensa perde acesso; cookies de produÃ§Ã£o tÃªm Secure; nenhum segredo aparece em logs. Confirmar tambÃ©m as proteÃ§Ãµes do proxy na homologaÃ§Ã£o.

### A02 â€” Pedidos nÃ£o tÃªm saÃ­das para imprevistos â€” P0

**Confirmado:** `OrderWorkflow.java:13â€“17` sÃ³ permite aceitar, marcar pronto, atribuir, retirar e entregar. O enum de `platform/apps/api/schema.sql` acompanha esse caminho. NÃ£o hÃ¡ recusa, cancelamento, expiraÃ§Ã£o, falha de entrega ou reatribuiÃ§Ã£o de pedido jÃ¡ atribuÃ­do.

**Impacto:** restaurante sem condiÃ§Ãµes de preparar e entregador indisponÃ­vel deixam pedidos sem resoluÃ§Ã£o suportada pelo sistema.

**Entrega:** matriz de estados e permissÃµes com motivo, autor e horÃ¡rio; definir janelas de cancelamento, recusa, reatribuiÃ§Ã£o e conclusÃ£o excepcional. Manter estado financeiro separado do estado logÃ­stico.

**Aceite:** cenÃ¡rios de recusa, cancelamento antes/depois do aceite, troca de entregador e falha de entrega chegam a um resultado auditÃ¡vel; transiÃ§Ãµes concorrentes nÃ£o geram eventos contraditÃ³rios.

### A03 â€” Checkout nÃ£o valida a versÃ£o/composiÃ§Ã£o confirmada â€” P1

**Risco derivado do cÃ³digo, sem reproduÃ§Ã£o em banco nesta sessÃ£o:** `CartService.java:78â€“92` lÃª o carrinho atual e compara somente `expectedTotalCents`. Se outra sessÃ£o trocar um item por outro do mesmo valor, o total continua igual e a compra pode conter itens diferentes dos que a primeira sessÃ£o exibiu.

AlÃ©m disso, `OrderController.java` mantÃ©m `POST /orders`, cujo contrato recebe itens e endereÃ§o, mas nÃ£o total esperado nem chave de idempotÃªncia. Repetir essa requisiÃ§Ã£o pode criar dois pedidos. O checkout do carrinho tem lock e limpeza transacional, o que jÃ¡ evita que duas chamadas simplesmente consumam o mesmo carrinho; ainda falta devolver o mesmo resultado apÃ³s perda de resposta/reenvio.

**Entrega:** versÃ£o ou identificador da cotaÃ§Ã£o/carrinho, validaÃ§Ã£o de composiÃ§Ã£o e preÃ§os, chave de idempotÃªncia por cliente e operaÃ§Ã£o. Unificar as garantias de todos os caminhos de criaÃ§Ã£o de pedido ou retirar o endpoint transitÃ³rio apÃ³s verificar consumidores.

**Aceite:** troca de item pelo mesmo preÃ§o exige nova confirmaÃ§Ã£o; retry da mesma operaÃ§Ã£o devolve o mesmo pedido; teste de resposta perdida e chamadas simultÃ¢neas nÃ£o gera duplicaÃ§Ã£o.

### A04 â€” OperaÃ§Ã£o bem-sucedida pode aparecer como erro â€” P1

**Confirmado no fluxo:** `platform/apps/web/app/page.tsx:103` coloca `await action()` e `await refresh(user)` no mesmo try/catch. Se o POST concluir e uma consulta posterior falhar, a funÃ§Ã£o retorna false e mostra erro. `refresh` agrega vÃ¡rias consultas em `Promise.all` antes de atualizar a interface.

**Impacto:** cliente ou administrador pode tentar novamente algo que jÃ¡ ocorreu; uma falha auxiliar impede atualizar os demais dados. No login/logout, o estado visual tambÃ©m depende do carregamento agregado.

**Entrega:** distinguir gravaÃ§Ã£o concluÃ­da de atualizaÃ§Ã£o da tela; usar o resultado da operaÃ§Ã£o, tratar falhas por seÃ§Ã£o e oferecer recarga sem repetir a mutaÃ§Ã£o. Tratar sessÃ£o expirada e falhas nÃ£o JSON do proxy com mensagens compreensÃ­veis.

**Aceite:** simular POST 201 seguido de GET 503; a UI confirma o pedido/cadastro e oferece somente recarregar os dados, sem sugerir nova criaÃ§Ã£o.

### A05 â€” FormulÃ¡rios perdem dados quando a API falha â€” P1

**Confirmado:** em `platform/apps/web/app/page.tsx:165â€“172`, os formulÃ¡rios de restaurante, categoria, produto, responsÃ¡vel e zona chamam `run(...)` e limpam campos imediatamente, sem aguardar sucesso. O formulÃ¡rio de faixa de CEP jÃ¡ usa o padrÃ£o correto de aguardar `ok`.

**Entrega e aceite:** aplicar o padrÃ£o de sucesso aos demais formulÃ¡rios. Resposta 400, 409 ou 503 deve preservar os dados relevantes, manter feedback de erro e permitir correÃ§Ã£o/reenvio. Tratar campos de senha conforme a polÃ­tica de seguranÃ§a definida.

### A06 â€” HistÃ³rico e fila param nos 100 pedidos mais novos â€” P1

**Confirmado:** `OrderService.java:102â€“105` aplica `LIMIT 100` para todos os papÃ©is, sem cursor nem filtros. â€œVer todosâ€ no cliente sÃ³ expande a lista jÃ¡ recebida.

**Impacto:** pedidos antigos deixam de ser acessÃ­veis pela listagem; um pedido ainda aberto pode sumir da fila apÃ³s novos pedidos. Os dados nÃ£o sÃ£o apagados do banco.

**Entrega:** separar fila ativa de histÃ³rico; paginaÃ§Ã£o estÃ¡vel, filtros por estado/data e busca por ID; Ã­ndices apÃ³s verificar plano de consulta.

**Aceite:** com mais de 100 pedidos e um pedido antigo aberto, a fila continua exibindo o aberto e o histÃ³rico permite navegar por todos sem duplicaÃ§Ãµes/lacunas.

### A07 â€” AdministraÃ§Ã£o nÃ£o sustenta operaÃ§Ã£o independente â€” P0 para piloto

**Confirmado no conjunto de controllers/tela inspecionado:** existe criaÃ§Ã£o de restaurantes, categorias, produtos e usuÃ¡rios de restaurante; entregadores apenas sÃ£o listados em `/admin/couriers`. NÃ£o hÃ¡ fluxo de cadastro/aprovaÃ§Ã£o/suspensÃ£o de entregador. TambÃ©m faltam ediÃ§Ã£o completa de catÃ¡logo e regras comerciais, horÃ¡rios/fechamento do restaurante e gestÃ£o de acessos.

**Impacto:** a demonstraÃ§Ã£o depende de seed/intervenÃ§Ã£o tÃ©cnica para completar cadastros e corrigir dados operacionais. Restaurante ativo nÃ£o equivale a restaurante aberto naquele horÃ¡rio.

**Entrega e aceite:** administrador monta uma operaÃ§Ã£o do zero sem SQL/seed: cadastra acessos e entregadores, configura cobertura, edita preÃ§os e horÃ¡rios, pausa lojas e produtos. Checkout respeita fechamento e disponibilidade.

### A08 â€” Pagamentos e conciliaÃ§Ã£o ainda nÃ£o existem na nova base â€” P0 para venda online

**Lacuna confirmada:** schema e serviÃ§os novos nÃ£o implementam cobranÃ§a, webhooks, estornos ou conciliaÃ§Ã£o; o README descreve pedidos demonstrativos. A pasta `payment-gateway` do legado nÃ£o Ã© uma integraÃ§Ã£o pronta para Java.

**Entrega:** decidir modalidades do piloto. Para pagamento na entrega, registrar mÃ©todo, valor devido e confirmaÃ§Ã£o de recebimento. Para pagamento online, integrar sandbox do provedor escolhido, validar callbacks, impedir processamento duplicado, tratar expiraÃ§Ã£o e reembolso e reconciliar valores. Definir comissÃ£o, frete e repasse com responsÃ¡veis do negÃ³cio antes de automatizÃ¡-los.

**Aceite:** pagamento aprovado/recusado/expirado, callback repetido e fora de ordem, divergÃªncia de valor e estorno tÃªm testes; nunca confiar apenas na tela de retorno do cliente para confirmar pagamento. NÃ£o hÃ¡ escolha de provedor nem orÃ§amento aprovado neste plano.

### A09 â€” OperaÃ§Ã£o depende de atualizaÃ§Ã£o manual â€” P1

**Confirmado na UI:** pedidos sÃ£o carregados na entrada, apÃ³s aÃ§Ãµes ou pelo botÃ£o de atualizaÃ§Ã£o. NÃ£o hÃ¡ polling periÃ³dico, stream ou notificaÃ§Ãµes no fluxo novo analisado.

**Impacto:** restaurante pode nÃ£o perceber novo pedido; cliente vÃª estado antigo atÃ© atualizar; nÃ£o existe alerta de atraso.

**Entrega:** atualizaÃ§Ã£o automÃ¡tica com reconexÃ£o e fallback, aviso de novo pedido e fila atrasada; notificaÃ§Ãµes externas com fila, retentativas e deduplicaÃ§Ã£o quando forem integradas.

**Aceite:** um pedido criado em outra sessÃ£o aparece dentro de um prazo acordado (sugestÃ£o inicial: atÃ© 10 segundos no piloto); perda de conexÃ£o fica visÃ­vel e reconexÃ£o recupera o estado correto.

### A10 â€” MigraÃ§Ã£o de banco nÃ£o tem recuperaÃ§Ã£o robusta â€” P1

**Risco concreto do desenho:** `platform/apps/api/src/migrate.ts` executa cada instruÃ§Ã£o e sÃ³ depois grava o nome da migration. `005_postal_coverage.sql`, por exemplo, cria uma tabela e depois altera endereÃ§os. Se houver interrupÃ§Ã£o entre os passos, a repetiÃ§Ã£o tenta criar a tabela novamente. NÃ£o hÃ¡ checksum nem bloqueio explÃ­cito de dois migradores. A API Java depende desse schema, mas seu contÃªiner nÃ£o o prepara.

**Entrega:** transferir migrations para um Ãºnico proprietÃ¡rio Java, com baseline verificado dos bancos existentes, lock, checksums e procedimento de recuperaÃ§Ã£o de DDL parcial. NÃ£o executar dois sistemas de migration em paralelo. Fazer backup/restauraÃ§Ã£o antes de aplicar em base relevante.

**Aceite:** banco vazio e cÃ³pia de banco existente chegam ao mesmo schema; nova execuÃ§Ã£o nÃ£o reaplica passos; falha simulada tem recuperaÃ§Ã£o documentada; alteraÃ§Ã£o retroativa de migration Ã© detectada.

### A11 â€” Testes e publicaÃ§Ã£o nÃ£o validam o produto completo â€” P1

**Confirmado:** `platform/package.json` executa somente testes TypeScript em `test`; `build` recursivo nÃ£o inclui Maven, pois Java nÃ£o Ã© pacote pnpm. NÃ£o foi encontrada pasta `.github` no workspace. O Dockerfile Java usa `-DskipTests`. Existem smokes Ãºteis, mas nÃ£o testes Java com MariaDB real na suÃ­te inspecionada.

**Entrega:** um comando/pipeline de verificaÃ§Ã£o para Java, tipos, builds, contratos, migrations e E2E dos quatro papÃ©is. Banco efÃªmero por execuÃ§Ã£o, separado de demonstraÃ§Ã£o e produÃ§Ã£o. Fixar versÃµes de ferramentas e instalaÃ§Ã£o pelo lockfile; substituir `latest` dos manifests por faixas deliberadas, sem perder o lock atual.

**Aceite:** clone limpo chega a um ambiente de teste reproduzÃ­vel; falha em qualquer componente bloqueia publicaÃ§Ã£o; smokes nÃ£o criam dados em banco real por engano. O uso de `latest` Ã© risco de atualizaÃ§Ã£o nÃ£o controlada, nÃ£o prova de vulnerabilidade.

### A12 â€” PublicaÃ§Ã£o e recuperaÃ§Ã£o da plataforma prÃ³pria estÃ£o incompletas â€” P0 para produÃ§Ã£o

**Confirmado na configuraÃ§Ã£o versionada e na VPS:** `deploy/docker-compose.yml` publica o legado. Foi adicionada uma composiÃ§Ã£o de homologaÃ§Ã£o independente em `platform/deploy/`, nomeada `foodie-staging`, com MariaDB isolado, etapa de migration, API Java, web Next e Caddy opcional. Em Docker local e na VPS, a migration concluiu, MariaDB e API ficaram saudÃ¡veis e o web respondeu HTTP 200 pela rede interna. Na VPS, a primeira subida nÃ£o abriu portas pÃºblicas nem reutilizou recursos do legado. A homologaÃ§Ã£o pÃºblica estÃ¡ disponÃ­vel em HTTPS por um host `sslip.io`, com certificado Let's Encrypt vÃ¡lido; enquanto o legado ocupar 80/443, a rota passa pelo Caddy jÃ¡ ativo, sem troca do domÃ­nio principal. Isso nÃ£o constitui aprovaÃ§Ã£o para produÃ§Ã£o. `/health` verifica `SELECT 1`, mas nÃ£o a versÃ£o do schema. NÃ£o hÃ¡ rotina de backup/restauraÃ§Ã£o e pipeline da nova pilha nesses arquivos. Isso nÃ£o prova ausÃªncia de backups externos na VPS.

**Entrega:** homologaÃ§Ã£o separada, imagens verificadas, configuraÃ§Ã£o explÃ­cita do proxy Next para a API na rede de contÃªineres, migrations como etapa controlada, readiness, logs com correlaÃ§Ã£o, mÃ©tricas, alertas, backup externo e ensaio de restauraÃ§Ã£o/rollback. Validar o momento em que `API_INTERNAL_URL` Ã© aplicado na construÃ§Ã£o/publicaÃ§Ã£o do Next.

**Aceite:** ambiente sobe a partir de documentaÃ§Ã£o; aplicaÃ§Ã£o sÃ³ recebe trÃ¡fego com schema correto; queda de banco Ã© detectada; restauraÃ§Ã£o e retorno Ã  versÃ£o anterior sÃ£o demonstrados com dados de teste.

## Planejamento por entregas

NÃ£o hÃ¡ informaÃ§Ã£o suficiente de equipe, carga de trabalho ou prazo comercial para prometer datas. As etapas abaixo sÃ£o marcos de aceite; estimar calendÃ¡rio depois de decompor as duas primeiras. Backend, frontend, QA e operaÃ§Ã£o sÃ£o responsabilidades, nÃ£o pressupÃµem quatro pessoas contratadas.

| Etapa | Entregas | ResponsÃ¡veis funcionais | DependÃªncia e conclusÃ£o |
| --- | --- | --- | --- |
| 1. Base verificÃ¡vel | Ambiente JDK/Node reproduzÃ­vel; comando de verificaÃ§Ã£o; MariaDB de testes; contratos atuais; documentaÃ§Ã£o de legado vs novo | Backend + QA/operaÃ§Ã£o | Entrada para todas as demais; fluxo atual passa em ambiente limpo |
| 2. CorreÃ§Ãµes de consistÃªncia | A03â€“A06; versÃ£o do carrinho; idempotÃªncia; separar gravaÃ§Ã£o/recarga; formulÃ¡rios; paginaÃ§Ã£o; migrations confiÃ¡veis | Backend + frontend + QA | Depende de 1; cenÃ¡rios de falha e concorrÃªncia passam |
| 3. OperaÃ§Ã£o real mÃ­nima | A02, A07, A09; cadastros completos; horÃ¡rios; recusa/cancelamento; entregadores; reatribuiÃ§Ã£o; atualizaÃ§Ã£o automÃ¡tica | Produto + backend + frontend | Depende de 2; equipe executa jornada e exceÃ§Ãµes sem editar banco |
| 4. Contas e dinheiro | A01 e A08; recuperaÃ§Ã£o/verificaÃ§Ã£o de conta; proteÃ§Ã£o contra abuso; mÃ©todo de pagamento; conciliaÃ§Ã£o e reembolso | Backend + frontend + responsÃ¡vel financeiro | ProteÃ§Ã£o de contas pode comeÃ§ar na etapa 2; piloto pÃºblico depende deste aceite |
| 5. HomologaÃ§Ã£o e piloto controlado | A11â€“A12; testes de carga; alertas; backup e rollback; ensaio de migraÃ§Ã£o; treinamento e suporte | QA + operaÃ§Ã£o + produto | Depende de 1â€“4; escopo pequeno de restaurantes/zonas com acompanhamento |
| 6. ExpansÃ£o do produto | Melhorias de experiÃªncia e comerciais, aplicativos prÃ³prios e automaÃ§Ã£o logÃ­stica | Produto + engenharia | Depois de medir estabilidade e demanda no piloto |

A sequÃªncia inicial de trabalho recomendada Ã©: preparar testes com banco â†’ corrigir confirmaÃ§Ã£o/idempotÃªncia do checkout e feedback da UI â†’ corrigir formulÃ¡rios e fila â†’ fechar estados excepcionais â†’ completar cadastros operacionais. SeguranÃ§a deve avanÃ§ar junto e bloquear exposiÃ§Ã£o pÃºblica atÃ© o aceite.

## Escopo para um produto mais completo

| Ãrea | Essencial para o piloto | EvoluÃ§Ã£o depois da base estÃ¡vel |
| --- | --- | --- |
| Cliente | Cadastro/recuperaÃ§Ã£o, escolha de endereÃ§o, catÃ¡logo por cobertura, carrinho confiÃ¡vel, pedido e pagamento, acompanhamento e suporte | Favoritos, recompra, avaliaÃ§Ãµes moderadas, cupons e fidelidade |
| CardÃ¡pio | EdiÃ§Ã£o de produto/preÃ§o/descriÃ§Ã£o/imagem, disponibilidade, horÃ¡rios; complementos quando necessÃ¡rios ao cardÃ¡pio piloto | Combos, adicionais com regras mÃ­nimas/mÃ¡ximas, variantes, estoque por perÃ­odo, agendamento |
| Restaurante | Fila ativa, aceite/recusa, prazo de preparo, pausa da loja, impressÃ£o se necessÃ¡ria e histÃ³rico | Equipe com permissÃµes, relatÃ³rios, mÃºltiplas unidades, integraÃ§Ã£o com cozinha/PDV |
| Entregador | Cadastro e aprovaÃ§Ã£o, disponibilidade, atribuiÃ§Ã£o, retirada, conclusÃ£o e ocorrÃªncia | Oferta/aceite de corridas, prova de entrega, localizaÃ§Ã£o consentida, navegaÃ§Ã£o, histÃ³rico de ganhos |
| AdministraÃ§Ã£o | GestÃ£o de acessos, zonas, lojas, entregadores, pedidos, cancelamentos e trilha administrativa | ComissÃµes, repasses, relatÃ³rios de desempenho e regras comerciais por estabelecimento |
| EndereÃ§os/logÃ­stica | Cobertura real cadastrada e validada, complemento/referÃªncia, taxa clara e endereÃ§o preservado no pedido | GeocodificaÃ§Ã£o, perÃ­metros, cÃ¡lculo de distÃ¢ncia, prazo estimado e despacho automÃ¡tico |
| Financeiro | SituaÃ§Ã£o do recebimento, estorno e conciliaÃ§Ã£o conforme modalidade escolhida | Extratos, repasses auditÃ¡veis, exportaÃ§Ã£o contÃ¡bil e polÃ­ticas comerciais avanÃ§adas |
| ExperiÃªncia | PortuguÃªs consistente, celular, teclado, foco, contraste, estados vazios/carregamento/erro, sessÃ£o expirada | Pesquisa com usuÃ¡rios, otimizaÃ§Ã£o de conversÃ£o e personalizaÃ§Ã£o |
| Suporte e dados | Canal de ajuda, tratamento de ocorrÃªncias, inventÃ¡rio de dados, retenÃ§Ã£o e controle de acesso definidos | Ferramentas de atendimento, solicitaÃ§Ãµes de exportaÃ§Ã£o/exclusÃ£o e mÃ©tricas operacionais |
| Mobile | Web responsivo validado nos dispositivos do piloto | Apps prÃ³prios apÃ³s contratos estabilizados; adaptar consumidores Flutter exige projeto especÃ­fico |

Cobertura por CEP nÃ£o confirma existÃªncia do endereÃ§o e nÃ£o calcula distÃ¢ncia. No MVP isso pode ser uma regra vÃ¡lida, desde que a Ã¡rea cadastrada corresponda ao atendimento e a operaÃ§Ã£o tenha procedimento para endereÃ§o incorreto. NÃ£o apresentar taxa fixa como cÃ¡lculo real de rota.

## Matriz mÃ­nima de validaÃ§Ã£o antes do piloto

1. Cliente A nÃ£o lÃª nem altera carrinho, endereÃ§o ou pedido de B; restaurante e entregador acessam apenas pedidos autorizados.
2. CEP invÃ¡lido/descoberto, mudanÃ§a de faixa, loja fechada, produto pausado e valor mÃ­nimo impedem checkout adequadamente.
3. Duas sessÃµes alterando carrinho; composiÃ§Ã£o muda com total igual; preÃ§o muda; tentativa concorrente; resposta perdida e retry.
4. Pedido transita pelo caminho normal e por recusa, cancelamento, expiraÃ§Ã£o, troca de entregador e falha de entrega com histÃ³rico correto.
5. Mais de 100 pedidos, pedidos abertos antigos e navegaÃ§Ã£o do histÃ³rico.
6. Pagamento conforme modalidade: falha, expiraÃ§Ã£o, repetiÃ§Ã£o de callback, valores divergentes e reembolso quando aplicÃ¡vel.
7. API fora do ar, resposta HTML do proxy, timeout e GET com falha depois de POST concluÃ­do; usuÃ¡rio nÃ£o perde dados nem Ã© induzido a duplicar aÃ§Ã£o.
8. Banco vazio, atualizaÃ§Ã£o de schema existente, interrupÃ§Ã£o de migration, backup e restauraÃ§Ã£o; nenhum teste destrutivo na produÃ§Ã£o.
9. Quatro papÃ©is em navegadores/sessÃµes distintos; comportamento mÃ³vel, teclado, reconexÃ£o e expiraÃ§Ã£o de sessÃ£o.
10. Carga compatÃ­vel com a quantidade acordada de lojas/pedidos, acompanhando latÃªncia, erros, pool de conexÃµes e fila; fixar metas apÃ³s mediÃ§Ã£o inicial.

## CritÃ©rio de liberaÃ§Ã£o e decisÃµes de produto

Liberar o piloto somente quando nÃ£o houver P0 aberto aplicÃ¡vel ao escopo, as correÃ§Ãµes P1 necessÃ¡rias ao fluxo estiverem verificadas, os quatro papÃ©is conseguirem operar sem SQL/seed, a matriz crÃ­tica passar e houver suporte, backup restaurado em teste e caminho de rollback. NÃ£o exigir todos os recursos comerciais da etapa 6 para comeÃ§ar a validar o negÃ³cio.

DecisÃµes a registrar antes das integraÃ§Ãµes: modalidades de pagamento, quem recebe/repassa valores, entrega prÃ³pria ou da plataforma, Ã¡rea inicial, horÃ¡rio de operaÃ§Ã£o, polÃ­tica de recusa/cancelamento, necessidade de adicionais/combos, quantidade de restaurantes e pedidos esperada e responsÃ¡vel por suporte. QuestÃµes de privacidade, termos e exigÃªncias contratuais devem ser verificadas com responsÃ¡veis competentes antes da publicaÃ§Ã£o; este documento nÃ£o estabelece conformidade legal.

Usar este documento como backlog consolidado e manter os planos anteriores como histÃ³rico de decisÃµes. Cada item deve receber responsÃ¡vel, issue, evidÃªncia de teste e estado de conclusÃ£o conforme a implementaÃ§Ã£o avance.
