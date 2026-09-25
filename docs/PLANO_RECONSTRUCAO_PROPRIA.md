# ReconstruÃ§Ã£o independente da plataforma

Atualizado em 24/09/2026. A primeira base local estÃ¡ em `platform/`; os serviÃ§os publicados na VPS nÃ£o foram alterados.

## DecisÃ£o tÃ©cnica

Construir as interfaces em Next.js/TypeScript e o backend prÃ³prio em **Java 21 com Spring Boot**, como serviÃ§o separado. A API Fastify em `platform/apps/api` Ã© um protÃ³tipo local de referÃªncia para fluxos e contratos; sua implementaÃ§Ã£o serÃ¡ substituÃ­da gradualmente pela API Java. NÃ£o alterar as verificaÃ§Ãµes de ativaÃ§Ã£o do pacote atual. Usar o sistema existente para entender os fluxos e a estrutura dos dados, sem copiar controladores, telas ou bibliotecas proprietÃ¡rias para a nova implementaÃ§Ã£o. Preservar os dados demonstrativos e migrÃ¡-los por scripts revisÃ¡veis.

O [Figma Foodie](https://www.figma.com/design/mlPWwBrTwJ53AHH4zC1gsT/Foodie---Food-Delivery-App-UI-Kit?node-id=727-25421) fornecido mostra telas mÃ³veis do cliente (entrada, cadastro, home, carrinho e outras). Ã‰ uma referÃªncia visual flexÃ­vel, nÃ£o um layout a copiar exatamente. Na tela inicial observada: fundo claro, verde como cor de aÃ§Ã£o, localizaÃ§Ã£o em destaque, oferta com fotografia de comida, atalhos de categorias e cards de recomendaÃ§Ãµes. Para admin, restaurante e entregador, criar interfaces prÃ³prias coerentes com essa identidade e com as tarefas de cada usuÃ¡rio. Imagens e Ã­cones sem licenÃ§a confirmada serÃ£o substituÃ­dos por recursos prÃ³prios.

## Arquitetura proposta

- **API independente:** monÃ³lito modular em Java/Spring Boot, com autenticaÃ§Ã£o, permissÃµes, catÃ¡logo, pedidos, zonas e histÃ³rico de estados implementados do zero. Banco MariaDB prÃ³prio, separado do banco legado. Migrations versionadas e transaÃ§Ãµes para pedido e mudanÃ§a de estado.
- **Admin web:** painel responsivo para administrar restaurantes, cardÃ¡pio, entregadores, clientes, zonas e pedidos. ComeÃ§ar com as operaÃ§Ãµes essenciais; relatÃ³rios, promoÃ§Ãµes e integraÃ§Ãµes entram depois.
- **Restaurante web:** painel responsivo para receber pedidos, aceitar/rejeitar, atualizar preparo e gerenciar disponibilidade de itens. Ã‰ o primeiro fluxo de operaÃ§Ã£o a integrar Ã  API prÃ³pria.
- **Entregador web mÃ³vel:** interface adaptada para celular para aceitar entrega, registrar retirada e conclusÃ£o e consultar histÃ³rico. A versÃ£o Flutter pode ser construÃ­da apÃ³s validar esse fluxo e os contratos da API.
- **Cliente:** preservar o site atual como referÃªncia de experiÃªncia enquanto a API prÃ³pria Ã© construÃ­da. Migrar gradualmente catÃ¡logo, autenticaÃ§Ã£o, carrinho e checkout para a nova API antes de desligar o backend antigo.
- **PublicaÃ§Ã£o:** novos contÃªineres e subdomÃ­nios de teste na mesma VPS, isolados da pilha atual. Trocar os endereÃ§os pÃºblicos apenas quando os fluxos passarem nos testes.
- **Crescimento:** API sem estado de sessÃ£o local, configuraÃ§Ã£o por ambiente, health checks, logs estruturados e mÃ©tricas. Escalar primeiro com Ã­ndices, consultas e capacidade da VPS; adicionar rÃ©plicas da API e processamento assÃ­ncrono quando as mediÃ§Ãµes justificarem. A escolha de Java por si sÃ³ nÃ£o garante escala.

## TransiÃ§Ã£o da API TypeScript para Java

1. Registrar os contratos HTTP e os casos de teste do protÃ³tipo atual: login/sessÃ£o, catÃ¡logo, zonas, endereÃ§os, criaÃ§Ã£o de pedido, transiÃ§Ãµes e erros. O Next.js continuarÃ¡ usando `/backend/*`.
2. Criar `platform/apps/api-java` com Java 21, Spring Boot, Maven, acesso ao MariaDB prÃ³prio e endpoint de saÃºde. Adotar Flyway quando a API Java assumir a propriedade das migrations; atÃ© lÃ¡, preservar a migraÃ§Ã£o do protÃ³tipo para evitar dois migradores concorrentes. Manter a API TypeScript disponÃ­vel apenas no ambiente local atÃ© a paridade.
3. Implementar um fluxo vertical por vez no Java, comeÃ§ando por catÃ¡logo e zonas; depois autenticaÃ§Ã£o/permissÃµes, pedidos e transiÃ§Ãµes. Essa primeira cobertura de cÃ³digo jÃ¡ existe; comparar respostas e regras com os testes do protÃ³tipo em MariaDB real antes de trocar o proxy.
4. Apontar o proxy `/backend/*` do Next.js para o Java no ambiente de teste apÃ³s a paridade dos fluxos essenciais. Exercitar o percurso cliente â†’ restaurante â†’ admin â†’ entregador â†’ cliente, incluindo cobertura, valores, autorizaÃ§Ã£o e histÃ³rico.
5. Publicar em contÃªiner isolado na VPS de teste, medir memÃ³ria, latÃªncia e consultas, validar backup e rollback; sÃ³ entÃ£o trocar o trÃ¡fego. Remover a API TypeScript quando nÃ£o houver mais consumidores.

## Ordem de construÃ§Ã£o

1. **InventÃ¡rio e dados:** mapear tabelas e relaÃ§Ãµes necessÃ¡rias, exportar um backup verificÃ¡vel, registrar fluxos de pedido e estados. Definir quais dados demonstrativos serÃ£o migrados e quais serÃ£o recriados.
2. **Base da API e admin mÃ­nimo:** login seguro, papÃ©is, zonas, restaurantes, categorias, produtos e visualizaÃ§Ã£o de pedidos. Aceite: um administrador consegue montar um cardÃ¡pio e acompanhar um pedido de teste sem acessar o pacote antigo.
3. **OperaÃ§Ã£o do restaurante:** login prÃ³prio, fila de pedidos, aceite, preparo, disponibilidade de itens. Aceite: o pedido passa de criado a pronto com histÃ³rico e autorizaÃ§Ã£o corretos.
4. **OperaÃ§Ã£o do entregador:** cadastro/aprovaÃ§Ã£o, atribuiÃ§Ã£o, aceite, retirada e conclusÃ£o. Aceite: o pedido passa de pronto a entregue e cliente/admin veem o mesmo estado.
5. **Cliente e pagamentos:** catÃ¡logo por zona, conta, carrinho, checkout, acompanhamento e notificaÃ§Ãµes. ComeÃ§ar com pedido demonstrativo ou pagamento manual; integrar meios de pagamento reais somente apÃ³s validar o fluxo.
6. **Acabamento:** aplicar o sistema visual baseado no Figma Ã s telas do cliente e adaptar cores, tipografia e componentes aos painÃ©is operacionais; revisar portuguÃªs, acessibilidade e uso no celular.

## Regras para preservar o projeto

- Nenhum ajuste no `ActivationCheckMiddleware` nem em `system-addons.php`.
- Nenhuma migraÃ§Ã£o destrutiva no banco publicado; usar cÃ³pia isolada e scripts idempotentes de importaÃ§Ã£o.
- NÃ£o colocar os aplicativos atuais em produÃ§Ã£o como se estivessem operacionais enquanto suas APIs de login retornarem 503.
- Registrar contratos da API prÃ³pria e testar os estados do pedido entre cliente, restaurante, entregador e admin.
- Manter o site atual disponÃ­vel atÃ© a troca controlada. O Foodie antigo permanece parado e preservado.

## Primeira entrega recomendada

O protÃ³tipo local estÃ¡ em `platform/`: API TypeScript transitÃ³ria, banco separado, painel web responsivo, quatro papÃ©is, catÃ¡logo, zonas configurÃ¡veis, endereÃ§os, taxa fixa por zona, pedido demonstrativo e histÃ³rico de estados. A API Java jÃ¡ cobre esses endpoints em cÃ³digo. O fluxo completo de pedidos foi validado anteriormente com MariaDB local; a nova cobertura por CEP ainda precisa de um teste integrado apÃ³s aplicar a migration `005_postal_coverage.sql`. A zona do endereÃ§o agora Ã© derivada de faixas de CEP cadastradas pelo admin e verificada novamente no checkout. Essa verificaÃ§Ã£o nÃ£o valida rua/nÃºmero nem substitui perÃ­metros geogrÃ¡ficos. Faltam cÃ¡lculo de distÃ¢ncia, pagamentos e notificaÃ§Ãµes. Consulte `platform/README.md` para executar e validar localmente.

## Pontos a confirmar antes de usar materiais externos

- Direito de uso do UI kit Foodie e de suas imagens/Ã­cones no produto final.
- LicenÃ§a ou titularidade do cÃ³digo atual. Se nÃ£o puder ser comprovada, ele ficarÃ¡ apenas como referÃªncia funcional durante a migraÃ§Ã£o e nÃ£o serÃ¡ copiado para a nova plataforma.
