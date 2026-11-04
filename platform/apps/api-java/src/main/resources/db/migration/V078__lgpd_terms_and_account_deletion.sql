-- LGPD mínima (spec docs/superpowers/specs/2026-11-04-lgpd-minimo-design.md):
-- aceite dos termos no cadastro e exclusão da conta pelo titular (anonimização).
ALTER TABLE users
  ADD COLUMN terms_accepted_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN terms_version VARCHAR(20) NULL DEFAULT NULL,
  ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

-- Rascunho dos textos legais. Só substitui o texto de exemplo do V045: edição feita no painel fica.
-- Precisa de revisão jurídica antes da produção.
UPDATE pages SET body = 'Estes Termos regulam o uso da plataforma Foodie, operada pela Axyon Software House. Ao criar uma conta, você declara que leu e concorda com eles e com a Política de privacidade.

## O que é o Foodie
O Foodie é uma plataforma que liga clientes a restaurantes. Cada restaurante é o vendedor: ele define o cardápio, os preços, o frete, o tempo de preparo e a entrega, e é quem recebe o pagamento do pedido. A Foodie fornece a tecnologia e é remunerada pelos restaurantes por assinatura; ela não vende comida e não recebe o valor dos pedidos.

## Sua conta
Você precisa ter 18 anos ou mais e informar dados verdadeiros. A conta é pessoal: guarde sua senha e avise o suporte se suspeitar de uso indevido. Podemos suspender contas usadas para fraude, abuso ou em desacordo com estes Termos.

## Pedidos e pagamentos
O pedido é um contrato entre você e o restaurante. Preço, disponibilidade e prazo são informados pelo restaurante no momento da compra. O pagamento online é processado pelo Mercado Pago na conta do próprio restaurante; o pagamento na entrega é feito diretamente ao restaurante ou ao entregador dele.

## Cancelamentos, estornos e reclamações
Cancelamentos e estornos seguem a Política de reembolso e são de responsabilidade do restaurante. Reclamações sobre o pedido (qualidade, itens faltando, atraso) devem ser feitas ao restaurante, pelo chat do pedido ou pelo pedido de reembolso no aplicativo. A Foodie apoia a conversa como suporte.

## Uso adequado
É proibido usar a plataforma para fraude, para enviar conteúdo ofensivo nas avaliações e no chat, para tentar acessar dados de outras pessoas ou para atrapalhar o funcionamento do serviço.

## Encerramento
Você pode excluir sua conta a qualquer momento em Perfil → Seus dados. Pedidos já feitos continuam no histórico do restaurante, sem os seus dados pessoais.

## Alterações e foro
Estes Termos podem mudar; avisaremos as mudanças relevantes no aplicativo. Aplica-se a legislação brasileira, incluindo o Código de Defesa do Consumidor.'
WHERE slug = 'termos' AND body = 'Descreva aqui as condições de uso da plataforma.';

UPDATE pages SET body = 'Esta Política explica como a plataforma Foodie trata os seus dados pessoais, nos termos da Lei Geral de Proteção de Dados (Lei nº 13.709/2018).

## Quem trata os dados
A Axyon Software House, que opera o Foodie, trata os dados da sua conta. O restaurante em que você faz um pedido recebe os dados necessários para preparar, entregar e cobrar esse pedido e passa a responder por eles também.

## Quais dados coletamos
- Cadastro: nome, e-mail, telefone e, se você entrar pelo Google ou Facebook, o identificador dessa conta.
- Pedidos: endereço de entrega, telefone de contato, itens, observações, valores e forma de pagamento.
- Uso: favoritos, interesses, avaliações, mensagens do chat do pedido e registros técnicos de acesso (data, hora e endereço IP).
- Dados do cartão não passam pelos nossos servidores: são digitados diretamente no formulário do Mercado Pago.

## Para que usamos
Para criar e proteger a sua conta, processar e entregar pedidos, permitir o contato com o restaurante e o entregador, cumprir obrigações legais, prevenir fraude e melhorar o serviço. Não vendemos os seus dados.

## Com quem compartilhamos
Com o restaurante do pedido e o entregador dele (só o necessário para a entrega), com o Mercado Pago (pagamento), com provedores de e-mail, SMS e hospedagem que trabalham para nós e com autoridades, quando a lei exigir.

## Por quanto tempo
Enquanto a conta existir. Ao excluir a conta, os seus dados pessoais são apagados ou anonimizados; o registro dos pedidos fica com o restaurante, sem identificar você, pelo prazo exigido pela legislação fiscal e de defesa do consumidor.

## Seus direitos
Você pode, a qualquer momento, baixar uma cópia dos seus dados e excluir a sua conta em Perfil → Seus dados. Para corrigir dados, revogar consentimentos ou tirar dúvidas, fale com o suporte pelo aplicativo.

## Cookies
Usamos apenas o cookie de sessão, necessário para manter você conectado.

## Segurança
Usamos conexão cifrada, senhas com hash, controle de acesso por papel e backups cifrados. Nenhum sistema é infalível; se houver um incidente que coloque você em risco, avisaremos.'
WHERE slug = 'privacidade' AND body = 'Descreva aqui como os dados dos clientes são tratados.';

UPDATE pages SET body = 'Cada restaurante é o vendedor do pedido e responde pelo reembolso. Esta Política descreve como isso funciona no Foodie.

## Antes de o restaurante aceitar
Você pode cancelar o pedido. Se já tiver pago online, o valor é estornado automaticamente na mesma forma de pagamento.

## Pedido recusado, cancelado pelo restaurante ou não aceito a tempo
O pagamento online é estornado automaticamente. Pedido pago na entrega não gera cobrança.

## Problemas com o pedido entregue
Use "Pedir reembolso" no pedido, explicando o que aconteceu. O restaurante analisa e decide; se aprovar, o estorno é feito na forma de pagamento original.

## Prazos do estorno
O Pix volta em geral no mesmo dia. No cartão de crédito, o estorno aparece na fatura conforme o prazo do banco emissor (normalmente até duas faturas).

## Contestação no banco
Se você contestar a compra no banco ou na bandeira do cartão, a disputa é tratada pelo restaurante, dono da venda. Recomendamos falar primeiro com o restaurante pelo chat do pedido.

## Suporte
Se não houver acordo com o restaurante, fale com o suporte do Foodie pelo aplicativo: nós ajudamos a mediar a conversa.'
WHERE slug = 'reembolso' AND body = 'Descreva aqui as regras de reembolso.';
