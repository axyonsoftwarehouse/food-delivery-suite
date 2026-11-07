-- Encarregado pelo tratamento de dados (LGPD, art. 41): nome e contato precisam estar publicados.
-- Decisão do dono em 07/11/2026: o encarregado é o Werner Saboia, e o contato é o e-mail atual da
-- Axyon até existir um e-mail no domínio de produção. Acrescenta a seção ao fim da Política de
-- privacidade sem apagar edições feitas no painel; não repete se a seção já existir.
UPDATE pages SET body = CONCAT(body, '

## Encarregado pelo tratamento de dados
O encarregado pelo tratamento de dados pessoais (LGPD, art. 41) é Werner Saboia. Para exercer os seus direitos, tirar dúvidas sobre esta Política ou falar sobre o tratamento dos seus dados, escreva para axyonsoftwarehouse@gmail.com.')
WHERE slug = 'privacidade' AND body NOT LIKE '%Encarregado pelo tratamento%';
