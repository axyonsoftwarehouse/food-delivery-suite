# Checklist de lançamento

Documento vivo. Criado em **07/11/2026** a partir de `ESTADO_ATUAL.md`, `REVISAO_UX_CLIENTE.md`,
`HOMOLOGACAO_ASSINATURA_P0.md` e dos roteiros de pagamento no staging.

Responde a uma pergunta: **o que falta para abrir a Foodie ao público?** O detalhe de cada item
fica no documento de origem; aqui fica só o item, o dono, o estado e a fonte.

> **Convenção.** Fato não confirmado entra como **`(a confirmar)`**. Ao fechar um item, troque o
> estado por **feito (dd/mm)** e diga onde está a prova (PR, pedido de teste, run do deploy).

**Donos:** **Werner** (decisão, contas e credenciais, que só o dono tem); **Código** (time/agente,
sai em PR); **Jurídico** (revisão externa); **Operação** (VPS, provedores, deploy).

**Estados:** pendente · em andamento · bloqueado (diz por quê) · feito (dd/mm).

## Resumo

O produto funciona de ponta a ponta no staging (cliente, loja, cozinha, entregador, pagamento
online com a conta da loja e estornos). O que separa isso do lançamento é, sobretudo, **não código**:
o ambiente de produção, a cobrança da assinatura (única receita), o e-mail real e o jurídico.

**Caminho crítico:** decidir a conta que recebe a assinatura (A1) → provar a cobrança no staging
(A2) → **teste de carga** (B9) → montar a produção (B1, B3–B6) → primeira transação real (F1).

## A. Receita — assinatura das lojas

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| A1 | Decidir a **conta da empresa** que recebe a assinatura | Werner | pendente (em discussão desde 14/10) | `ESTADO_ATUAL.md` §4, decisões em aberto |
| A2 | Configurar a conta de teste da assinatura no staging (`SUBSCRIPTIONS_*`, cobrança real ligada) | Werner | bloqueado: credenciais só com o dono | `HOMOLOGACAO_ASSINATURA_P0.md` §2 |
| A3 | Rodar a homologação da P0: reativar as três assinaturas, ver as cobranças Pix nascerem e pagar uma | Código + Werner | bloqueado por A2 | `HOMOLOGACAO_ASSINATURA_P0.md` |
| A4 | Roteiro funcional da assinatura no painel da loja (Pix e cartão com 3DS) | Código + Werner | bloqueado por A2 | `ESTADO_ATUAL.md` §4 |

## B. Ambiente de produção

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| B1 | Domínio de produção e DNS | Werner | pendente | `ESTADO_ATUAL.md` §4, prontidão |
| B2 | Onde roda a produção | Werner | feito (07/11): **na VPS atual** | decisão do dono |
| B2.1 | Definir se o staging continua na mesma VPS depois da virada (memória de 4 GB e disco) ou sai | Werner + Operação | pendente | `RUNBOOK_VPS.md` |
| B3 | Aplicação Mercado Pago de **produção** (OAuth das lojas, webhook, URL de produção no painel) | Werner | pendente | `ESTADO_ATUAL.md` §3, "Falta para fechar" item 5 |
| B4 | Chave Pix registrada na conta (o provedor exige para produção) | Werner | pendente `(a confirmar)` | `ESTADO_ATUAL.md` §3, item 4 |
| B5 | **Trocar todas as credenciais** (Mercado Pago, segredos de webhook, senhas de teste); nenhuma de teste vai para produção | Werner + Operação | pendente (decisão de 14/10) | `ESTADO_ATUAL.md` §3, item 3 |
| B6 | Banco de produção nasce limpo: sem dados e contas de demonstração do staging | Código + Operação | pendente | `REVISAO_UX_CLIENTE.md`, "Dados do staging" |
| B7 | Backup diário cifrado no Cloudflare R2 (bucket `foodie-backups`) e ensaio de restauração | Operação | feito (R2 em 20/10, ensaio em 04/11); na virada, conferir que o backup pega o banco de produção | `RUNBOOK_VPS.md` §5 |
| B8 | Sentry na API e no site (projetos `foodie-api` e `foodie-web`) | Operação | feito (PRs #65–#67); na virada, conferir o ambiente marcado nos eventos `(a confirmar)` | `ESTADO_ATUAL.md` §4 |
| B9 | **Teste de carga na VPS** — prioridade máxima | Código + Operação | em andamento (07/11): plano e scripts prontos, validados no ambiente local; falta publicar, rodar o `load:setup` na VPS e fazer as rodadas | `PLANO_TESTE_CARGA.md` |

## C. Contas e comunicação com o cliente

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| C1 | Configurar o **SMTP** real com o **Resend** | Werner + Operação | em andamento (07/11): Resend escolhido e conta criada; falta verificar o domínio e gravar `MAIL_*`/`SPRING_MAIL_*` no `.env`. Sem domínio verificado, o Resend só envia para o e-mail da própria conta | `ESTADO_ATUAL.md` §4 |
| C1.1 | SPF, DKIM e DMARC no domínio de produção, para o e-mail não cair no spam | Werner + Operação | bloqueado por B1 e C1 | — |
| C2 | Testar no staging o link de verificação de e-mail e o de recuperação de senha | Código | bloqueado por C1 | `ESTADO_ATUAL.md` §4 |
| C3 | Confirmar se o cadastro trava sem e-mail verificado (define se C1 é bloqueador) | Código | `(a confirmar)` | `ESTADO_ATUAL.md`, cabeçalho (rodada de 21/10) |
| C4 | Provedor de SMS de produção para o login por código (cotas e custo) | Werner + Operação | `(a confirmar)` | — |

## D. Jurídico e LGPD

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| D1 | Revisão jurídica de termos, privacidade e reembolso (editáveis em Conteúdo → Páginas) | Jurídico | pendente (rascunho no ar desde 04/11) | `ESTADO_ATUAL.md` §4, LGPD mínima |
| D2 | Indicar o **encarregado** (LGPD, art. 41): a pessoa que atende os titulares e a ANPD. Pode ser alguém da Foodie (o próprio dono) ou um serviço contratado. Nome e contato (ex.: um e-mail de privacidade) vão na política de privacidade. Agente de pequeno porte pode ficar dispensado de nomear, mas continua obrigado a manter o canal `(a confirmar com o jurídico)` | Werner + Jurídico | pendente | idem |
| D3 | Licença do kit visual de terceiros (Figma) | Werner | não se aplica (07/11): o kit só serviu de inspiração, e nada dele foi usado | decisão do dono |
| D4 | Quem opera: marca própria ou cliente (afeta termos e contas) | Werner | pendente | idem |
| D5 | Reescrita do histórico do Git para tirar nomes de fornecedor (repositório público) | Código + equipe | pendente (adiada até a equipe pausar) | memória do projeto `(a confirmar)` |

## E. Produto e experiência

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| E1 | Conferir no staging os itens 5 a 7 da revisão de UX | Código | em parte (07/11): PR #108 mesclada e publicada (`852f11e`); item 5 conferido como visitante no celular (o toque na descrição do prato sem opções abre a ficha, sem mexer no carrinho, e "Adicionar" coloca o prato no carrinho). Os itens 6 e 7 pedem um pedido com cliente logado | `REVISAO_UX_CLIENTE.md` |
| E2 | Conferir a rolagem do `/entrar` num celular real (cardápio sem login) | Werner | pendente | `REVISAO_UX_CLIENTE.md`, item 2 |
| E3 | UX item 8: página pública da loja com aberto/fechado, horário, taxa e mínimo | Código | pendente | `REVISAO_UX_CLIENTE.md` |
| E4 | UX item 9: o login cita Google e Facebook, mas só existe SMS | Código | pendente | idem |
| E5 | UX item 10: "Indique esta loja" acima do cardápio | Código | pendente | idem |
| E6 | UX item 13: loja sem foto mostra foto de hambúrguer | Código | pendente | idem |
| E7 | UX itens 16 e 17: alvos de toque abaixo de 44 px e contraste abaixo de 4,5:1 | Código | pendente | idem |
| E8 | UX itens 11, 12, 14, 15 e 18 (detalhes) | Código | pendente; podem ir depois do lançamento | idem |
| E9 | Homologar no staging recorrência e campanhas | Código + Werner | pendente (código testado, nunca visto publicado) | `ESTADO_ATUAL.md` §3 |

## F. Pagamento do pedido (conta da loja)

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| F1 | **Primeira transação real em produção**: um Pix de valor baixo com a conta real de uma loja, webhook 200 e estorno. Não repete os roteiros do staging: confere só o que o staging não prova (credenciais trocadas, segredo do webhook e aplicação de produção) | Werner + Código | bloqueado por B1, B3–B5 | — |
| F2 | Nova tentativa: cartão `OTHE` recusado e depois `APRO` no mesmo pedido | Código + Werner | feito (07/11): testado repetidas vezes no staging, segundo o dono | decisão do dono |
| F3 | Ver o formulário do cartão destravar depois de "Ocorreu um erro" | Código + Werner | feito (07/11): testado no staging, segundo o dono | decisão do dono |
| F4 | Decidir se basta o aviso para quem usa bloqueador (Firefox rígido, Brave, uBlock quebram o formulário do cartão) | Werner | pendente | `PAGAMENTOS_MODO_TESTE.md` §7 |
| F5 | Limpar do `.env` as variáveis sem uso (`MERCADOPAGO_SANDBOX`, `MERCADOPAGO_NOTIFICATION_URL`) | Operação | pendente | `ESTADO_ATUAL.md` §3, item 7 |

## Depois do lançamento

Não bloqueiam a abertura: Apple Sign In, driver S3, carteira do cliente como meio de pagamento,
contrato dos módulos, relatórios do admin que ainda expõem dados individuais, tokens do Mapbox
`(a confirmar)` e documentação de operação consolidada (`ESTADO_ATUAL.md` §4, prioridade média).

## Critério de "pronto para lançar"

- Seções **A, B, C e D** inteiras em **feito** (ou com a decisão registrada de lançar sem o item).
- Na seção **E**, de E1 a E7 feitos.
- **F1** passou em produção.
- `VERIFY_INTEGRATION=1 pnpm verify` verde no commit publicado e o `.deployed` de produção conferido.
