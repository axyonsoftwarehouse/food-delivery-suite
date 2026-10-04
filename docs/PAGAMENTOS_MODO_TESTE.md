# Pagamentos em modo de teste (Mercado Pago) — passo a passo

Guia para fazer um Pix ou um pagamento com cartão **de mentira** no staging e confirmar que o pedido
virou **pago** no Foodie. Nenhum dinheiro real se move: as credenciais do staging são de **teste**.

## 1. Como o modo de teste funciona (o mínimo para não se perder)

- **As credenciais de teste pertencem a um usuário de teste, não à sua conta.** No painel do Mercado
  Pago, em *Suas integrações → App-Checkout-Transparente-Foodie → Credenciais de teste*, o token e a
  public key são do vendedor de teste `TESTUSER4062510080958592865` (User ID `3588446200`). Por isso
  as orders criadas no staging têm id `ORDTST…`.
- **Quem decide o resultado do teste é o PRIMEIRO NOME do cliente.** O Foodie manda o primeiro nome do
  cliente logado como `payer.first_name`, e o Mercado Pago usa esse nome para escolher o resultado
  simulado. Um cliente chamado **"APRO Cliente Teste"** tem todo pagamento **aprovado**.

  | Primeiro nome do cliente | Resultado |
  |---|---|
  | `APRO` | aprovado |
  | `OTHE` | recusado (erro geral) |
  | `CONT` | pendente |
  | `CALL` | recusado — precisa autorizar (o Foodie ainda não trata esse caso) |
  | `FUND` | recusado — saldo insuficiente |
  | `SECU` | recusado — código de segurança inválido |
  | `EXPI` | recusado — data de validade |
  | `FORM` | recusado — erro no formulário |

- **O aviso de "pago" chega por webhook.** Quando a order muda de status, o Mercado Pago chama
  `https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago`. Essa URL está configurada em
  *App-Checkout-Transparente-Foodie → Webhooks → Modo de teste*, com o evento **Order (Mercado Pago)**.
  **Não clique em "Salvar configurações" nem em "Redefinir" nessa tela sem necessidade:** cada clique
  gera uma assinatura secreta nova, e aí o segredo gravado no staging deixa de valer (todo webhook
  passa a voltar `401`). Se trocar, grave o novo com `platform/deploy/gravar-segredo-webhook.bat`.

## 2. Preparar um cliente de teste (uma vez só)

1. Abra `https://cliente.staging.2.29.42.104.sslip.io` (ou `https://staging.2.29.42.104.sslip.io/loja`).
2. Crie uma conta de cliente com **nome começando por `APRO`** — por exemplo `APRO Cliente Teste`.
   Já existe uma no staging com esse nome (`cliente.foodie.1791072319151@gmail.com`); use-a se souber a senha.
3. Use um **email com domínio real** (gmail, outlook…). Domínios como `@demo.local` são recusados pelo
   Mercado Pago (`payer.email must be a valid email`).

## 3. Fazer um Pix de teste

1. Logado como o cliente `APRO…`, escolha um restaurante, ponha um item no carrinho e vá ao checkout.
2. Escolha **Pix** e confirme. A tela mostra um QR Code e o "copia e cola" — **não precisa pagar nada**.
3. Com o nome `APRO`, o Mercado Pago aprova o Pix sozinho, **em geral entre 2 e 10 minutos**.
4. Quando o webhook chega, o pedido aparece como **pagamento aprovado** e o restaurante pode aceitá-lo.

## 4. Fazer um pagamento de teste com cartão

1. Mesmo cliente `APRO…`, mesmo caminho até o checkout; escolha **Cartão**.
2. Preencha o formulário com um **cartão de teste público do Mercado Pago**:

   | Bandeira | Número | CVV | Validade |
   |---|---|---|---|
   | Mastercard | `5031 4332 1540 6351` | `123` | `11/30` |
   | Visa | `4235 6477 2802 5682` | `123` | `11/30` |
   | American Express | `3753 651535 56885` | `1234` | `11/30` |

   - **Nome no cartão:** `APRO` (ou o mesmo código do resultado que você quer testar).
   - **CPF:** `12345678909`.
3. Confirme. O cartão é aprovado na hora; o webhook confirma em seguida.

Para testar uma **recusa**, use um cliente cujo primeiro nome seja `OTHE` (ou `FUND`, `SECU`…).

## 5. Como conferir se deu certo

- **Na tela:** o pedido mostra "Pagamento aprovado" para o cliente e aparece para o restaurante.
- **Nos logs do staging** (precisa da chave SSH do projeto):

  ```bash
  ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 "docker logs --since 30m foodie-staging-api-1 2>&1 | grep -E 'webhooks|recusado'"
  ```

  - `POST /webhooks/mercadopago -> 200` → a notificação foi aceita.
  - `-> 401` com `Webhook do Mercado Pago recusado` → o segredo gravado não é o do painel (veja o
    aviso da seção 1) ou a assinatura mudou de formato; a linha mostra o manifesto usado.
- **No painel do Mercado Pago:** *Webhooks* mostra cada envio, com o código HTTP da resposta.

## 6. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Pix fica "aguardando pagamento" para sempre | o primeiro nome do cliente não é `APRO`, ou o webhook está voltando `401` |
| Todo webhook volta `401` | segredo do staging diferente do painel (alguém salvou/redefiniu a tela de Webhooks) |
| Nenhum webhook chega | URL apagada do painel, ou configurada só em "Modo de produção" |
| Checkout responde `409` | `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` está `false` no `.env` do staging |
| Erro "payer.email must be a valid email" | email do cliente com domínio que não existe |
| "Simular notificação" do painel com id `123456` | volta `200` e é ignorado de propósito: não é um pedido nosso. Para um teste real, use o id `ORDTST…` de uma order existente |
