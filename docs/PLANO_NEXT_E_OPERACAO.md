# Plano de atualização do Next e operação local

Atualizado em 23/09/2026. Este documento é um plano; nenhuma dependência foi atualizada nesta etapa.

## Situação verificada

- `web/` usa Next 12.1.6, React 17 e Pages Router (46 arquivos em `src/pages`). O `npm run build` concluiu depois de limitar o heap do Node a 3 GB, usar um worker e desativar `esmExternals` no Next 12.
- A falha anterior era no carregamento do bundle de `_app` durante `Collecting page data`. Apenas aumentar o limite de 3 para 4 GB não resolveu; alterar `esmExternals` resolveu e o build passou com Node 20 e Node 24.
- `web/.env.production` tem `NEXT_PUBLIC_BASE_URL` e `NEXT_CLIENT_HOST_URL` vazios. Enquanto isso, páginas renderizadas no servidor, como `/` e `/home`, retornam 500. Em desenvolvimento, o backend está configurado como `http://127.0.0.1:8000`.
- `web/src/pages/search/index.js` usa `process.NEXT_PUBLIC_BASE_URL` em vez de `process.env.NEXT_PUBLIC_BASE_URL`; `/search` também retorna 500.
- A minificação de produção está desativada em `web/next.config.js`; o build passa, mas o JavaScript enviado ao navegador fica maior. Há ainda um aviso do Moment sobre data sem formato explícito em `web/src/components/checkout-page/CheckoutPage.jsx`.
- A máquina tem aproximadamente 12 GB de RAM e 99 GB livres em disco. Node 24, PHP 8.4, Composer e Flutter estão instalados. O repositório contém MariaDB 11.4.2 e Nginx locais.
- Há milhares de renomeações e outras alterações em andamento no Git. Preservar esse estado ao criar o ambiente isolado da atualização.

## O que roda neste repositório

| Parte | Como é servida localmente | Dependências para funcionar |
| --- | --- | --- |
| Backend/admin Laravel | Porta 8000 por `iniciar-admin.bat` (PHP) ou `iniciar-web.bat` (Nginx + PHP-CGI) | MariaDB, PHP 8.2–8.4, dependências Composer, `.env` e banco inicializado |
| Site Next (`web/`) | Porta 3000 por `npm run dev` ou `npm run start` | Node, `node_modules`, backend em 8000 para páginas com SSR, `.env.development` ou `.env.production` conforme o modo |
| Apps Flutter cliente/restaurante/entregador | Portas 8081/8082/8083 por `iniciar-web.bat` | Builds em `app-*/build/web`; gerados por `build-web.bat` com Flutter |

`iniciar-web.bat` **não inicia o site Next**. Hoje os três diretórios de build Flutter não têm `index.html`; iniciar os servidores estáticos antes de `build-web.bat` não entrega os aplicativos. Escolher `iniciar-admin.bat` **ou** `iniciar-web.bat` para a porta 8000, evitando subir os dois ao mesmo tempo.

## Rodar o site Next agora, antes da atualização

1. Subir MariaDB e Laravel com `iniciar-admin.bat` **ou** a pilha Nginx/PHP com `iniciar-web.bat`. Confirmar resposta HTTP 200 em `http://127.0.0.1:8000/api/v1/config` antes de abrir o Next.
2. Para desenvolvimento, entrar em `web/` e usar `npm run dev`. `web/.env.development` já aponta para `http://127.0.0.1:8000` e para o front em `http://localhost:3000`. Confirmar `/`, `/home`, `/search` e `/checkout` no navegador; `/search` precisa da correção acima.
3. Para testar o build de produção **localmente**, configurar em `web/.env.production` a URL base do Laravel (`http://127.0.0.1:8000`) e a origem do site (`http://localhost:3000`), sem acrescentar `/api/v1` à URL base. Então executar `npm run build` e `npm run start` em `web/`. Refazer o build sempre que mudar uma variável `NEXT_PUBLIC_*`, pois ela entra no código servido ao navegador.
4. Não colocar senha ou token secreto em variáveis `NEXT_PUBLIC_*`. Firebase para notificações e Mapbox para mapas são integrações separadas da URL base do backend.

Para os apps Flutter, usar `build-web.bat` e depois `iniciar-web.bat`. Os builds Flutter não dependem do `next build`; cada aplicativo tem seu próprio processo de compilação.

## Implantação fora da máquina de desenvolvimento

O pacote `_local_server` e os arquivos `.bat` são uma conveniência para Windows local. O desenho de produção precisa destes serviços, com processos supervisionados para reiniciar após falhas:

1. Banco MySQL/MariaDB com dados persistentes, backups testados e acesso restrito ao backend.
2. Laravel com PHP compatível, dependências Composer, `APP_URL`, conexão de banco, chave da aplicação, `storage` persistente e servidor HTTP/PHP. Verificar se tarefas agendadas e filas precisam de workers próprios; hoje o ambiente local usa `QUEUE_CONNECTION=sync`, cache e sessões em arquivo.
3. Site Next como processo Node para SSR, atrás de HTTPS/reverse proxy. O servidor precisa alcançar a URL pública ou interna da API; o navegador precisa alcançar a URL pública configurada em `NEXT_PUBLIC_BASE_URL`. `NEXT_CLIENT_HOST_URL` deve refletir a origem do site.
4. Três builds Flutter Web servidos como arquivos estáticos, caso os aplicativos cliente, restaurante e entregador façam parte da implantação. Firebase, Mapbox, email e gateways de pagamento são habilitados/configurados conforme as funcionalidades escolhidas.
5. DNS, TLS, logs, monitoramento de disponibilidade e memória, testes HTTP de `/api/v1/config` e rotas SSR, além de um procedimento de rollback para o release anterior.

Para um primeiro piloto, medir separadamente RAM e CPU de Next em execução, PHP e banco sob uso real antes de escolher o tamanho definitivo de VPS. O pico de `next build` é uma carga temporária: compilar em CI ou outra máquina evita dimensionar o servidor de produção pelo pior pico de compilação. Se o build ocorrer na própria VPS, reservar memória extra para ele e não executá-lo ao mesmo tempo que builds Flutter.

## Roteiro de atualização

### 0. Estabilizar e registrar a referência atual — 0,5 a 1 dia

- Resolver URLs dos ambientes, o erro de `/search` e o aviso de data do Moment.
- Verificar backend, banco e rotas principais com o Next 12 atual. Registrar versões de Node/npm, tempo e pico de RAM do build, telas críticas e comportamento de login, carrinho, checkout, mapas e notificações.
- Criar um ambiente de trabalho isolado **preservando** as alterações ainda não consolidadas do repositório; não assumir que um worktree novo incluirá mudanças locais.
- Critério de saída: `npm run build` passa e as rotas SSR principais deixam de retornar 500 com o backend ativo.

### 1. Atualizar dependências React e remover incompatibilidades — 1 a 3 dias

- Subir React 17 para 18 e alinhar React DOM e bibliotecas relacionadas. Inspecionar as dependências com peer dependencies antigas: `@material-ui/core` v4, `react-google-login` e `react-facebook-login`. O `framer-motion` instalado já exige React 18.
- Substituir pacotes incompatíveis em vez de manter `--force`/`--legacy-peer-deps` como solução permanente. Registrar um lockfile reproduzível e validar uma instalação limpa.
- Critério de saída: instalação limpa, renderização e interações básicas sem erros de hidratação ou de dependências.

### 2. Avançar o Next por versões principais, mantendo Pages Router — 2 a 4 dias

- Aplicar as mudanças documentadas de 12 → 13 → 14 → 15, com build e teste rápido entre versões. O alvo intermediário recomendado é a linha 15.5.26, que recebe manutenção em 23/09/2026.
- Revisar e remover opções antigas do `next.config.js` conforme cada versão; não carregar automaticamente os contornos de memória do Next 12 para as versões novas. Reativar a minificação e medir bundle e RAM.
- Confirmar páginas SSR, páginas estáticas, imagens, redirects, estilos MUI/Emotion, login, carrinho e checkout. Não há obrigação de migrar as 46 páginas para App Router nesta etapa.
- Critério de saída: build com limite de memória conhecido, servidor de produção funcional e fluxos principais testados.

### 3. Alvo Next 16 LTS — 2 a 4 dias adicionais, condicionado à fase 2

- Avaliar a versão 16.3.6 ou patch posterior da linha 16.3 e a combinação React compatível com as dependências do projeto. Next 16 requer Node 20.9+; o Node 24 local atende esse requisito.
- Next 16 usa Turbopack por padrão e este projeto tem configuração Webpack customizada. Começar com `next build --webpack` para isolar a atualização do framework; depois remover/migrar as customizações e testar Turbopack.
- Atualizar o comando de build com base em medições reais de heap e RAM, e validar novamente os mesmos fluxos da fase 2.
- Critério de saída: build reproduzível, operação de produção e smoke test completos, sem aumento descontrolado de memória.

Estimativa total inicial: **aproximadamente 1–2 semanas de trabalho e validação** para chegar à linha 16 com segurança. A faixa pode aumentar se bibliotecas antigas de autenticação/social, mapas ou MUI exigirem substituição mais extensa. O pacote Next não tem custo de licença; o custo é trabalho de compatibilidade, testes e infraestrutura.

## Plano de recursos e proteção contra novo pico de memória

- **Agora:** os 12 GB da máquina foram suficientes para o build corrigido. Manter o `npm run build` com heap de 3 GB como limite de segurança. Esse limite é do heap V8 de cada processo, **não** da RAM total usada pelo Node e seus workers.
- **Durante builds:** executar um build pesado por vez. Não compilar os três apps Flutter, o Next e um emulador Android simultaneamente; fechar builds antigos antes de iniciar outro. Manter espaço livre para `.next`, `node_modules`, logs e cache. Limpar apenas caches identificados quando houver evidência de corrupção ou crescimento anormal.
- **Se houver novo crescimento:** registrar fase do build, versão de Node/Next, PID, memória privada do processo, RAM livre e stderr antes de alterar o limite. Nas versões recentes, usar os instrumentos de diagnóstico de memória do Next. Interromper uma tentativa que se aproxime continuamente do limite sem progresso.
- **Hardware:** não há evidência de necessidade de comprar RAM apenas para validar o Next atual. Se for necessário trabalhar com Laravel, Next, Flutter, navegador e emulador ao mesmo tempo, 16 GB é um mínimo prático e 24–32 GB oferecem mais folga; decidir após medir uma sessão de trabalho real. Dimensionamento de servidor de produção exige teste de carga separado e não deve ser inferido do pico de build local.
- **Ambiente reproduzível:** fixar a versão de Node usada pela equipe/CI, manter lockfile, usar instalação limpa no CI e registrar os comandos de inicialização e checagens de saúde. O build deve falhar se faltar uma URL pública obrigatória, em vez de produzir uma aplicação que retorna 500 ao iniciar.

## Fontes para executar a atualização

- [Guias oficiais de upgrade do Next](https://nextjs.org/docs/app/guides/upgrading)
- [Pages Router continua suportado](https://nextjs.org/docs/pages)
- [Mudanças do Next 16 e opção `--webpack`](https://nextjs.org/docs/app/guides/upgrading/version-16)
- [Versões 15.5.26 e 16.3.6 anunciadas em 22/09/2026](https://nextjs.org/blog/nextjs-security-update-september-22-2026)
- [Guia oficial de memória do Next](https://nextjs.org/docs/app/guides/memory-usage)
- [Compilação web do Flutter](https://docs.flutter.dev/platform-integration/web/building)
