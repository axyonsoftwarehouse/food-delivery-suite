# Referência funcional para a plataforma independente

Levantamento de funcionalidades do pacote antigo, apenas como inspiração de produto. A nova implementação tem domínio, contratos e interface próprios. O visual de cliente segue como referência flexível o [Figma Foodie](https://www.figma.com/design/mlPWwBrTwJ53AHH4zC1gsT/Foodie---Food-Delivery-App-UI-Kit?node-id=727-25421).

## Já coberto no protótipo Java

- Catálogo por restaurante, categoria, produto e zona; valor mínimo e taxa fixa por zona.
- Cadastro de restaurante, responsável, zona e produto pelo administrador.
- Sessão com papéis de cliente, restaurante, entregador e admin.
- Endereço, pedido e histórico do ciclo criado → aceito → pronto → atribuído → retirado → entregue.
- Cadastro público de cliente e disponibilidade de produto controlada pelo próprio restaurante.
- Home do cliente com direção visual Foodie, busca e filtro de categorias no servidor com paginação por cursor, e carrinho de vários itens do mesmo restaurante, salvo por cliente no servidor.
- Carrinho compartilhado entre sessões e dispositivos, importação do carrinho local anterior e checkout transacional com conferência de valores.

## Próximas capacidades, em ordem sugerida

1. **Qualidade da busca:** a home já consulta pratos disponíveis por zona, texto e categoria em páginas de até 30 itens. Evoluir a relevância e a indexação textual quando o catálogo crescer.
2. **Atualizações do carrinho:** o carrinho já fica na conta do cliente. Evoluir da atualização manual para notificações em tempo real quando outro dispositivo alterar os itens.
3. **Operação do restaurante:** alternar disponibilidade de pratos e acompanhar a fila de preparo; depois definir horário de funcionamento e fechamento temporário. O pacote antigo oferece controle de produto e estado do restaurante.
4. **Conta e comunicação:** confirmar email, recuperar senha e avisar cada papel sobre mudanças importantes do pedido. O legado expõe cadastro, recuperação e notificações; implementar com contratos e mensagens próprios.
5. **Promoções e avaliação:** cupons e avaliações depois de estabilizar preços, pagamentos e entregas. O legado contém essas funções, mas dependem de regras adicionais e não entram no pedido demonstrativo.

## Critério de avanço

O site já usa a API Java no ambiente local e o smoke completo passou com MariaDB de teste. Antes de publicar: confirmar autorização entre restaurantes sob carga, testar pedidos simultâneos e transações, e concluir proteção do cadastro público. Não conectar a API nova ao banco publicado do sistema antigo.
