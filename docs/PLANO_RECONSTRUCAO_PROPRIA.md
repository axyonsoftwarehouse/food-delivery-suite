# Reconstrução independente da plataforma

Atualizado em 24/09/2026. A primeira base local está em `platform/`; os serviços publicados na VPS não foram alterados.

## Decisão técnica

Construir as interfaces em Next.js/TypeScript e o backend próprio em **Java 21 com Spring Boot**, como serviço separado. A API Fastify em `platform/apps/api` é um protótipo local de referência para fluxos e contratos; sua implementação será substituída gradualmente pela API Java. Não alterar as verificações de ativação do pacote atual. Usar o sistema existente para entender os fluxos e a estrutura dos dados, sem copiar controladores, telas ou bibliotecas proprietárias para a nova implementação. Preservar os dados demonstrativos e migrá-los por scripts revisáveis.

O [Figma Foodie](https://www.figma.com/design/mlPWwBrTwJ53AHH4zC1gsT/Foodie---Food-Delivery-App-UI-Kit?node-id=727-25421) fornecido mostra telas móveis do cliente (entrada, cadastro, home, carrinho e outras). É uma referência visual flexível, não um layout a copiar exatamente. Na tela inicial observada: fundo claro, verde como cor de ação, localização em destaque, oferta com fotografia de comida, atalhos de categorias e cards de recomendações. Para admin, restaurante e entregador, criar interfaces próprias coerentes com essa identidade e com as tarefas de cada usuário. Imagens e ícones sem licença confirmada serão substituídos por recursos próprios.

## Arquitetura proposta

- **API independente:** monólito modular em Java/Spring Boot, com autenticação, permissões, catálogo, pedidos, zonas e histórico de estados implementados do zero. Banco MariaDB próprio, separado do banco legado. Migrations versionadas e transações para pedido e mudança de estado.
- **Admin web:** painel responsivo para administrar restaurantes, cardápio, entregadores, clientes, zonas e pedidos. Começar com as operações essenciais; relatórios, promoções e integrações entram depois.
- **Restaurante web:** painel responsivo para receber pedidos, aceitar/rejeitar, atualizar preparo e gerenciar disponibilidade de itens. É o primeiro fluxo de operação a integrar à API própria.
- **Entregador web móvel:** interface adaptada para celular para aceitar entrega, registrar retirada e conclusão e consultar histórico. A versão Flutter pode ser construída após validar esse fluxo e os contratos da API.
- **Cliente:** preservar o site atual como referência de experiência enquanto a API própria é construída. Migrar gradualmente catálogo, autenticação, carrinho e checkout para a nova API antes de desligar o backend antigo.
- **Publicação:** novos contêineres e subdomínios de teste na mesma VPS, isolados da pilha atual. Trocar os endereços públicos apenas quando os fluxos passarem nos testes.
- **Crescimento:** API sem estado de sessão local, configuração por ambiente, health checks, logs estruturados e métricas. Escalar primeiro com índices, consultas e capacidade da VPS; adicionar réplicas da API e processamento assíncrono quando as medições justificarem. A escolha de Java por si só não garante escala.

## Transição da API TypeScript para Java

1. Registrar os contratos HTTP e os casos de teste do protótipo atual: login/sessão, catálogo, zonas, endereços, criação de pedido, transições e erros. O Next.js continuará usando `/backend/*`.
2. Criar `platform/apps/api-java` com Java 21, Spring Boot, Maven, acesso ao MariaDB próprio e endpoint de saúde. Adotar Flyway quando a API Java assumir a propriedade das migrations; até lá, preservar a migração do protótipo para evitar dois migradores concorrentes. Manter a API TypeScript disponível apenas no ambiente local até a paridade.
3. Implementar um fluxo vertical por vez no Java, começando por catálogo e zonas; depois autenticação/permissões, pedidos e transições. Essa primeira cobertura de código já existe; comparar respostas e regras com os testes do protótipo em MariaDB real antes de trocar o proxy.
4. Apontar o proxy `/backend/*` do Next.js para o Java no ambiente de teste após a paridade dos fluxos essenciais. Exercitar o percurso cliente → restaurante → admin → entregador → cliente, incluindo cobertura, valores, autorização e histórico.
5. Publicar em contêiner isolado na VPS de teste, medir memória, latência e consultas, validar backup e rollback; só então trocar o tráfego. Remover a API TypeScript quando não houver mais consumidores.

## Ordem de construção

1. **Inventário e dados:** mapear tabelas e relações necessárias, exportar um backup verificável, registrar fluxos de pedido e estados. Definir quais dados demonstrativos serão migrados e quais serão recriados.
2. **Base da API e admin mínimo:** login seguro, papéis, zonas, restaurantes, categorias, produtos e visualização de pedidos. Aceite: um administrador consegue montar um cardápio e acompanhar um pedido de teste sem acessar o pacote antigo.
3. **Operação do restaurante:** login próprio, fila de pedidos, aceite, preparo, disponibilidade de itens. Aceite: o pedido passa de criado a pronto com histórico e autorização corretos.
4. **Operação do entregador:** cadastro/aprovação, atribuição, aceite, retirada e conclusão. Aceite: o pedido passa de pronto a entregue e cliente/admin veem o mesmo estado.
5. **Cliente e pagamentos:** catálogo por zona, conta, carrinho, checkout, acompanhamento e notificações. Começar com pedido demonstrativo ou pagamento manual; integrar meios de pagamento reais somente após validar o fluxo.
6. **Acabamento:** aplicar o sistema visual baseado no Figma às telas do cliente e adaptar cores, tipografia e componentes aos painéis operacionais; revisar português, acessibilidade e uso no celular.

## Regras para preservar o projeto

- Nenhum ajuste no `ActivationCheckMiddleware` nem em `system-addons.php`.
- Nenhuma migração destrutiva no banco publicado; usar cópia isolada e scripts idempotentes de importação.
- Não colocar os aplicativos atuais em produção como se estivessem operacionais enquanto suas APIs de login retornarem 503.
- Registrar contratos da API própria e testar os estados do pedido entre cliente, restaurante, entregador e admin.
- Manter o site atual disponível até a troca controlada. O Foodie antigo permanece parado e preservado.

## Primeira entrega recomendada

O protótipo local está em `platform/`: API TypeScript transitória, banco separado, painel web responsivo, quatro papéis, catálogo, zonas configuráveis, endereços, taxa fixa por zona, pedido demonstrativo e histórico de estados. A API Java já cobre esses endpoints em código. O fluxo completo de pedidos foi validado anteriormente com MariaDB local; a nova cobertura por CEP ainda precisa de um teste integrado após aplicar a migration `005_postal_coverage.sql`. A zona do endereço agora é derivada de faixas de CEP cadastradas pelo admin e verificada novamente no checkout. Essa verificação não valida rua/número nem substitui perímetros geográficos. Faltam cálculo de distância, pagamentos e notificações. Consulte `platform/README.md` para executar e validar localmente.

## Pontos a confirmar antes de usar materiais externos

- Direito de uso do UI kit Foodie e de suas imagens/ícones no produto final.
- Licença ou titularidade do código atual. Se não puder ser comprovada, ele ficará apenas como referência funcional durante a migração e não será copiado para a nova plataforma.
