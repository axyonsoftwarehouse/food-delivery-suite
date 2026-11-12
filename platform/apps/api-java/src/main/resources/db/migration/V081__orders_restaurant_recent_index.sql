-- Teste de carga de 11/11/2026: a lista de pedidos encerrados da loja (GET /orders, consultada a
-- cada 8 s por tela de loja) faz WHERE restaurant_id = ? AND status IN (...) ORDER BY id DESC LIMIT 100.
-- Sem um índice que já entregue os pedidos da loja em ordem de id, o banco preferia varrer a tabela
-- inteira pela chave primária (10.269 linhas lidas para achar 100 no staging), e o custo crescia com
-- o total de pedidos de todas as lojas. Com (restaurant_id, id) ele anda só pelos pedidos da loja.
CREATE INDEX ix_orders_restaurant_recent ON orders (restaurant_id, id);
