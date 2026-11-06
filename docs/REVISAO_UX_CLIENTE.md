# Revisão de UI/UX — fluxo do cliente (04/11/2026)

Revisão feita no staging (`staging.2.29.42.104.sslip.io`), no celular (375 px) e no
computador (1366 px), nos temas claro e escuro, com a conta de cliente de teste.
Foi feito um pedido de teste (#107, dinheiro), cancelado em seguida.

Fluxo percorrido: home → página pública da loja → entrar → início logado → loja →
item com opções → carrinho → pedido → acompanhamento → cancelamento → perfil.

**Andamento (06/11):** itens **1, 3 e 4 corrigidos** (PR #104). Item **2 resolvido** na branch
`feat/cardapio-sem-login` (pendente de PR e conferência no staging). Item **8 em parte**: o cardápio já
abre sem login, mas a página pública da loja ainda não mostra aberto/fechado, horário, taxa e mínimo.

**Impressão geral:** a parte logada está boa — início com "Peça de novo", loja com
tempo/taxa/mínimo, ficha do item com preço que atualiza, carrinho completo, linha do
tempo do pedido e perfil com LGPD. Os problemas maiores estão **antes do login** e
em alguns detalhes de feedback.

## Graves — resolver antes do lançamento

1. **A tela de entrar expõe as contas de demonstração.** O email vem preenchido com
   `admin@demo.local` e o campo sugere admin, restaurante, entregador e cliente
   `@demo.local` para qualquer visitante.
   - `platform/apps/web/app/app-context.tsx:174` (`useState('admin@demo.local')`)
   - `platform/apps/web/app/entrar/page.tsx:77` (`<datalist id="demo-users">`)
   - Correção: começar vazio e mostrar a lista só em desenvolvimento.

2. **Não dá para ver o cardápio sem criar conta.** "Fazer um pedido" (home) leva ao
   login, e "Ver pratos e pedir" (página pública da loja) vai para `/loja`, que exige
   usuário (`platform/apps/web/app/loja/layout.tsx:105`). O visitante não vê prato
   nem preço antes de se cadastrar — é onde mais se perde cliente.
   - Correção: cardápio público em `/restaurantes/[id]` e login só ao finalizar o
     pedido, voltando para o carrinho depois de entrar.
   - **Resolvido (06/11, branch `feat/cardapio-sem-login`):** o visitante vê o cardápio, informa o
     CEP e monta o carrinho sem login; o login só é pedido antes do pagamento, e o carrinho do
     visitante vai para a conta depois de entrar. Falta a conferência no celular, no staging.

## Médios

3. **O título da aba vira sempre "Foodie • Plataforma independente".** Um efeito
   global sobrescreve o título de toda página, inclusive o da loja e o novo título
   da home (que conta para o Google). `platform/apps/web/app/app-context.tsx:278`.
   Correção: só mexer no título no painel/cozinha (contador de pedidos) e preservar
   o título da página.

4. **Erro de opção obrigatória fica escondido.** Na ficha do item, ao tocar em
   "Adicionar" sem escolher uma opção obrigatória ("Ponto da carne"), a mensagem
   aparece no fim da área rolável, atrás dos botões — a pessoa só vê que nada
   aconteceu. `platform/apps/web/app/loja/restaurant-menu.tsx:122` e `:270`.
   Correção: rolar até o grupo e destacá-lo (ou desabilitar "Adicionar" com o motivo).
   O texto "opção(ões)" também pode virar "Escolha 1 opção em …".

5. **Tocar no prato não abre o prato.** Só o botão "+" abre a ficha; tocar no nome,
   na descrição ou na foto não faz nada. No celular o card inteiro deveria abrir.

6. **O pedido não mostra as opções escolhidas.** Em "Itens" aparece só
   "1 × Bowl de frango grelhado"; no carrinho aparecia "Grande · Bacon · Ao ponto".
   O cliente não consegue conferir o que pediu. `platform/apps/web/app/OrderDetails.tsx:125`.

7. **"Rastrear entrega" aparece em pedido cancelado** (e antes de sair para entrega).
   `platform/apps/web/app/OrderDetails.tsx:140` — mostrar só com o pedido a caminho.

8. **Página pública da loja quase vazia.** Antes do login, mostra só capa, frase e três
   botões. Faltam: aberta/fechada, horário, tempo e taxa de entrega, pedido mínimo e
   o cardápio (ver item 2).

9. **Texto do login cita Google e Facebook, mas só existe SMS.**
   `platform/apps/web/app/entrar/page.tsx:106` — montar a frase com os provedores
   configurados.

10. **O cartão "Indique esta loja" fica acima do cardápio.** Na primeira tela da loja
    (celular) não aparece nenhum prato. Sugestão: mover para depois do cardápio ou
    deixar compacto.

## Detalhes

11. Distância com ponto decimal: "28.4 km" → "28,4 km"
    (`platform/apps/web/app/loja/carrinho/page.tsx:215`, usar `toLocaleString('pt-BR')`).
12. Cancelar exige motivo digitado (mín. 3 caracteres). Para o cliente, motivos
    prontos (demorou, pedi errado, mudei de ideia, outro) são mais rápidos.
13. Lojas sem foto mostram a foto de hambúrguer (inclusive sushi e vegana). Usar uma
    capa neutra com a inicial e pedir a capa no cadastro da loja.
14. No celular, o título da tela de entrar fica parcialmente atrás da foto.
15. Perfil sem editar nome/telefone nem trocar senha; "Pontos 35" sem dizer quanto valem.
16. Botões de favoritar (30×30 px) e filtros (30 px de altura) abaixo do mínimo
    recomendado de 44 px para toque.
17. Contraste: descrições dos pratos e "Aberto agora" em 4,28:1 (mínimo 4,5:1) e o
    número do sino de notificações em 2,94:1.
18. No tema escuro, o nome da loja fixo no topo fica cortado sob a busca ao rolar.

## O que está bem

- Acessibilidade básica: `lang="pt-BR"`, todos os botões com nome, campos com rótulo,
  imagens com `alt`, respeito a "reduzir movimento".
- Ficha do item com preço atualizando e limites claros ("Até 3", "Escolha 1").
- Carrinho com resumo, taxa, previsão e botão fixo com o total.
- Acompanhamento com etapas, previsão de chegada e "Falar com a loja".
- Validação do troco no servidor (troco menor que o total é recusado).

## Dados do staging (não são bugs de código)

- "Bowl de frango grelhado" marcado como **Vegano** e com "Ponto da carne".
- Todas as lojas com o mesmo tempo (45–55 min) e taxa (R$ 5,99).
- Endereço do cliente a 28 km da loja.

## Sugestão de ordem

1. Itens 1 e 3 (rápidos, antes do lançamento).
2. Item 2 (cardápio público) — o de maior impacto em conversão; pede um desenho curto.
3. Itens 4 a 7 (feedback e conferência do pedido).
4. Restante, em lote.
