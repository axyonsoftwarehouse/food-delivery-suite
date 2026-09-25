# ReferÃªncia funcional para a plataforma independente

Levantamento de funcionalidades do pacote antigo, apenas como inspiraÃ§Ã£o de produto. A nova implementaÃ§Ã£o tem domÃ­nio, contratos e interface prÃ³prios. O visual de cliente segue como referÃªncia flexÃ­vel o [Figma Foodie](https://www.figma.com/design/mlPWwBrTwJ53AHH4zC1gsT/Foodie---Food-Delivery-App-UI-Kit?node-id=727-25421).

## JÃ¡ coberto no protÃ³tipo Java

- CatÃ¡logo por restaurante, categoria, produto e zona; valor mÃ­nimo e taxa fixa por zona.
- Cadastro de restaurante, responsÃ¡vel, zona e produto pelo administrador.
- SessÃ£o com papÃ©is de cliente, restaurante, entregador e admin.
- EndereÃ§o, pedido e histÃ³rico do ciclo criado â†’ aceito â†’ pronto â†’ atribuÃ­do â†’ retirado â†’ entregue.
- Cadastro pÃºblico de cliente e disponibilidade de produto controlada pelo prÃ³prio restaurante.
- Home do cliente com direÃ§Ã£o visual Foodie, busca e filtro de categorias no servidor com paginaÃ§Ã£o por cursor, e carrinho de vÃ¡rios itens do mesmo restaurante, salvo por cliente no servidor.
- Carrinho compartilhado entre sessÃµes e dispositivos, importaÃ§Ã£o do carrinho local anterior e checkout transacional com conferÃªncia de valores.

## PrÃ³ximas capacidades, em ordem sugerida

1. **Qualidade da busca:** a home jÃ¡ consulta pratos disponÃ­veis por zona, texto e categoria em pÃ¡ginas de atÃ© 30 itens. Evoluir a relevÃ¢ncia e a indexaÃ§Ã£o textual quando o catÃ¡logo crescer.
2. **AtualizaÃ§Ãµes do carrinho:** o carrinho jÃ¡ fica na conta do cliente. Evoluir da atualizaÃ§Ã£o manual para notificaÃ§Ãµes em tempo real quando outro dispositivo alterar os itens.
3. **OperaÃ§Ã£o do restaurante:** alternar disponibilidade de pratos e acompanhar a fila de preparo; depois definir horÃ¡rio de funcionamento e fechamento temporÃ¡rio. O pacote antigo oferece controle de produto e estado do restaurante.
4. **Conta e comunicaÃ§Ã£o:** confirmar email, recuperar senha e avisar cada papel sobre mudanÃ§as importantes do pedido. O legado expÃµe cadastro, recuperaÃ§Ã£o e notificaÃ§Ãµes; implementar com contratos e mensagens prÃ³prios.
5. **PromoÃ§Ãµes e avaliaÃ§Ã£o:** cupons e avaliaÃ§Ãµes depois de estabilizar preÃ§os, pagamentos e entregas. O legado contÃ©m essas funÃ§Ãµes, mas dependem de regras adicionais e nÃ£o entram no pedido demonstrativo.

## CritÃ©rio de avanÃ§o

O site jÃ¡ usa a API Java no ambiente local e o smoke completo passou com MariaDB de teste. Antes de publicar: confirmar autorizaÃ§Ã£o entre restaurantes sob carga, testar pedidos simultÃ¢neos e transaÃ§Ãµes, e concluir proteÃ§Ã£o do cadastro pÃºblico. NÃ£o conectar a API nova ao banco publicado do sistema antigo.
