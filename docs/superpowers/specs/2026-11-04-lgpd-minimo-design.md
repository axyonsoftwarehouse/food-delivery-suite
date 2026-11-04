# LGPD mínima para o lançamento — desenho

Data: 04/11/2026. Origem: análise de prontidão de lançamento (bloqueadores 3 e 4). O dono do produto
pediu para seguir sem perguntas ("confio no seu taco"), então **todas as escolhas abaixo são do
agente** e podem ser revistas na revisão da PR.

## Intenção

Ter o mínimo que a LGPD exige de um app que guarda nome, e-mail, telefone e endereço:
o titular **sabe** o que é feito com os dados (termos e política públicos), **concorda** ao criar a
conta, **leva** os próprios dados e **apaga** a conta sozinho, sem depender do suporte.

## Fora de escopo

- Texto jurídico definitivo: o rascunho entra no banco e é editável no painel (Conteúdo → Páginas),
  mas **precisa de revisão por advogado antes da produção** `(a confirmar com o dono)`.
- Novo aceite quando os termos mudarem (pedir de novo a quem já aceitou uma versão antiga).
- Exclusão self-service para loja, entregador e admin: essas contas estão ligadas a uma loja e
  saem pelo suporte ou pela própria loja (Equipe). A exportação vale para qualquer papel.
- Banner de cookies: o site só usa o cookie de sessão (estritamente necessário).
- Indicação de encarregado (DPO) e canal de e-mail próprio `(a confirmar com o dono)`.

## Decisões

### 1. Páginas legais públicas

- Rota pública `/paginas/[slug]` no site, renderizada no servidor a partir de
  `GET /public/pages/{slug}`. O texto é simples: parágrafos separados por linha em branco,
  e linhas que começam com `## ` viram subtítulos. Sem HTML: nada de XSS vindo do painel.
- Links para **Termos de uso**, **Política de privacidade** e **Política de reembolso** no rodapé da
  home, da tela de entrada e no Perfil do cliente.
- A migration `V078` troca o texto de exemplo ("Descreva aqui…") de `termos`, `privacidade` e
  `reembolso` por um rascunho coerente com o modelo do Foodie (a loja vende e responde por
  pedido, frete, estorno e chargeback; a Foodie cobra assinatura e opera a plataforma). Só troca
  quando o corpo ainda é o texto de exemplo, para não apagar edição feita no painel.

### 2. Aceite no cadastro

- `users.terms_accepted_at` e `users.terms_version` (`V078`). A versão é uma constante no código
  (`LegalTerms.VERSION`, hoje `2026-11-04`), trocada quando o texto mudar de forma relevante.
- `POST /auth/signup` passa a exigir `acceptTerms: true` (400 sem ele). O formulário ganha a caixa
  "Li e aceito os Termos de uso e a Política de privacidade", obrigatória.
- Conta criada por Google, Facebook ou SMS grava o aceite na criação; a tela mostra, junto desses
  botões, "Ao continuar, você concorda com os Termos de uso e a Política de privacidade".
- Contas antigas ficam com o aceite vazio (ver fora de escopo).

### 3. Exportação dos dados — `GET /me/data-export`

Qualquer papel logado. Devolve um JSON para download (`foodie-meus-dados.json`) com: conta (nome,
e-mail, telefone, criação, verificação, aceite e versão dos termos, provedor social), endereços,
pedidos (loja, status, tipo, valores, endereço e telefone do pedido, itens), avaliações de loja e de
entregador, favoritos, extrato de pontos, interesses e mensagens de chat enviadas.

### 4. Exclusão da conta — `POST /me/delete-account`

- Só **cliente** (outros papéis: 403 com "Fale com o suporte").
- Confirmação: o corpo leva `confirm: "EXCLUIR"`. Conta de senha (sem provedor social e sem ser
  conta de telefone) também leva `password`, conferida fora da transação da anonimização, para a
  tentativa errada contar no limite (5 erros travam 15 min; senha errada responde 403, não 401,
  porque a sessão continua válida). `GET /me/privacy` informa `passwordLogin` e `canDelete` para a
  tela saber se pede a senha e se mostra o botão.
- Bloqueada com pedido em andamento (status fora dos terminais do `OrderService`): 409.
- É **anonimização**, não `DELETE`: os pedidos apontam para o usuário sem cascata e a loja precisa
  do histórico de vendas (obrigação fiscal e exercício de direitos — base legal do art. 16).
  Numa transação:
  - `users`: nome "Conta excluída", e-mail `excluida-{id}@excluida.invalid`, telefone, código de
    indicação, provedor social e verificação limpos, senha aleatória, `suspended_at` com o motivo
    "Conta excluída pelo titular" e `deleted_at` (`V078`). A suspensão já barra login, sessão e
    recuperação de senha; reativar uma conta excluída fica proibido.
  - Apagados: sessões, tokens de ação, OTP do telefone, carrinho, endereços (o pedido perde o
    `address_id`, mantém o texto anonimizado), favoritos, interesses, notificações, inscrições de
    push e tokens de aparelho, pedidos recorrentes, pontos, indicações, chaves de idempotência e
    mensagens de chat.
  - Pedidos: `delivery_address_text` vira "Endereço removido a pedido do titular";
    `contact_phone` e a nota geral (`customer_note`, que costuma ter dica de endereço) ficam vazios.
    Valores, itens (com a observação do item) e status ficam.
  - Avaliações ficam, sem nome (o nome vem do usuário anonimizado).
- Depois: o cookie é apagado e um e-mail avisa a exclusão (exceto conta de telefone, que não tem
  e-mail real). O log registra só o id.
- Saldo de cashback e pontos se perde; a tela avisa antes.

## O que fica para quem implementa

Nomes de classes, layout do diálogo e o texto exato do rascunho jurídico, desde que sigam o acima.
