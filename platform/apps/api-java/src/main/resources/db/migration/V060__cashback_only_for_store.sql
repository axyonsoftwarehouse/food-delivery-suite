-- Decisão de 05/10/2026: o cashback é financiado pela loja, não pela plataforma.
-- As regras globais (restaurant_id IS NULL) deixam de valer. Nenhuma linha é
-- apagada: o histórico é preservado e a mudança vale daqui pra frente.
UPDATE cashback_rules SET active = FALSE WHERE restaurant_id IS NULL AND active = TRUE;
