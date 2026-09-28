# Carteira do restaurante — avaliação de produto

> Atualização em 28/09/2026: a descontinuação foi implementada. Novos créditos e solicitações da loja foram bloqueados, o painel saiu da navegação e o pagamento online global foi suspenso para novos pedidos. Esta avaliação registra o diagnóstico anterior; as pendências atuais estão em `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`.

## Decisão recomendada

Se o contrato da Foodie cobra apenas a assinatura da plataforma e cada restaurante recebe diretamente os pagamentos dos pedidos, a carteira de **saque do restaurante** não representa um valor a receber da Foodie. Recomendo retirá-la do fluxo ativo depois da conciliação dos saldos e repasses existentes. O financeiro privado da loja (vendas, despesas e resultados) continua pertinente e é uma função diferente da carteira de repasse.

Essa conclusão depende de **todos** os meios de pagamento, inclusive Pix e cartão online, liquidarem diretamente ao restaurante. Hoje `OnlinePaymentService` usa um gateway resolvido pela plataforma, sem escolha de conta por restaurante; o código não demonstra liquidação direta por lojista. Nesse arranjo, remover os repasses antes de mudar a integração de pagamento deixaria um valor recebido pela plataforma sem caminho de acerto.

## Dependências existentes

| Parte | Comportamento atual | Mudança necessária no modelo proposto |
| --- | --- | --- |
| `LedgerService.postOrder` | Credita venda ao restaurante e debita comissão por pedido | Parar de criar saldo sacável da loja; registrar vendas somente no financeiro privado e cobranças da assinatura em razão próprio da plataforma. |
| `WalletController` e `PayoutService` | Permitem saldo, métodos e solicitações de saque do restaurante | Encerrar novas solicitações após conciliar as abertas; manter histórico somente para consulta. |
| `FinanceController` | Admin enxerga saldo e extrato individuais e decide repasses | Limitar a assinatura, cobranças, GMV agregado e eventuais acertos históricos. |
| `/painel/carteira` | Exibe saldo “pronto para saque” ao restaurante | Retirar do menu da loja após o corte contábil; preservar a carteira do entregador se a Foodie ainda lhe repassar frete e gorjeta. |
| Pagamento online | O gateway é selecionado sem uma conta de recebimento do restaurante | Implementar conta/credenciais por loja ou provedor com divisão e liquidação contratualmente definida. |
| `subscription_packages` | Permite `commission_percent` além do preço periódico | Definir pacotes só com mensalidade se comissão por pedido foi descartada; migrar contratos existentes sem alterar cobranças passadas. |

## Sequência de migração

1. Confirmar o recebedor de Pix, cartão, dinheiro e pagamento manual, além de quem remunera o entregador e responde por estornos.
2. Inventariar saldos da carteira, solicitações de repasse abertas e pedidos online ainda não conciliados. Fechar cada caso com um registro auditável.
3. Alterar a integração online para liquidação direta ao lojista e testar pagamento, cancelamento e reembolso por loja.
4. Desabilitar novos créditos e saques da carteira de restaurante; manter consulta histórica e retirar a navegação da loja e os controles de repasse do admin.
5. Separar cobrança da assinatura SaaS do fluxo de pedidos e remover comissão dos novos contratos, caso a política final seja exclusivamente mensalidade.

Não apagar lançamentos, pedidos ou solicitações históricas. A carteira do cliente (créditos/recompensas) e a do entregador têm finalidades próprias e exigem decisões separadas.
