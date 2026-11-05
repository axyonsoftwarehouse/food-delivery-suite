# Cardápio e carrinho sem login — desenho (05/11/2026)

Item 2 da revisão de UX do cliente (`docs/REVISAO_UX_CLIENTE.md`): hoje o visitante não vê prato
nem preço antes de criar conta. Este documento descreve a mudança.

## Por quê

O cliente chega, na maioria das vezes, por um **link da loja** (WhatsApp, Instagram, Google). Hoje
"Ver pratos e pedir" leva a `/loja`, que exige usuário (`app/loja/layout.tsx`), e o "Fazer um
pedido" da home leva ao login. É o ponto onde mais se perde cliente: ninguém cria conta sem saber
o que a loja vende e quanto custa.

**Referência conferida em 05/11 (iFood, site, deslogado):** ele pede o endereço antes de listar as
lojas, guarda o endereço só no navegador, mostra cardápio e ficha do prato sem conta e exige a
conta **ao adicionar o primeiro item**. A sacola fica vazia até a pessoa entrar.

**Decisão do dono do produto (Werner, 05/11):** o visitante vai **até antes do pagamento** sem
conta, ou seja, monta o carrinho inteiro. A conta é exigida para pagar, e assim o pedido fica
vinculado a ela. É mais generoso que o iFood: quando a conta é pedida, a pessoa já investiu tempo
no carrinho.

## Objetivos

1. Quem chega pelo link da loja vê o cardápio completo (fotos, preços, busca, categorias, tags)
   e a ficha do prato, sem conta.
2. O visitante informa o **CEP** sem conta e vê se a loja entrega ali, a taxa e o tempo estimados.
3. O visitante monta o carrinho (variações, adicionais, observação por item e quantidade) sem
   conta. O carrinho fica guardado no navegador.
4. No carrinho, o botão de continuar leva ao login ou cadastro. Depois de entrar, a pessoa
   **volta para o carrinho com tudo o que montou**, e o pedido segue como hoje.

## Fora do escopo (não fazer agora)

- Pedido sem conta (checkout como convidado).
- Carrinho de visitante guardado no servidor.
- Renderizar o cardápio no servidor para o Google (SEO do cardápio). A página pública
  `/restaurantes/[id]` continua sendo a página indexável.
- Os outros itens da revisão (5 a 18), exceto o que este desenho já cobre.

## Anti-objetivos (seria uma falha mesmo funcionando)

- A pessoa entra ou se cadastra e **perde o carrinho**, ou cai na home logada em vez do carrinho.
- A tela do visitante dispara chamadas que exigem login e enche o console ou o Sentry de **401**.
- Algum dado de cliente, como endereço ou telefone, fica disponível sem sessão.
- O preço que o visitante vê difere do preço cobrado sem aviso.

## O percurso

1. **Entrada.** São três portas, que chegam ao mesmo cardápio:
   - **link da loja** `/restaurantes/[id]`: "Ver pratos e pedir" passa a levar a
     `/loja/restaurantes/[id]`, e não mais a `/loja`;
   - **home**: os cartões de restaurante já são públicos e levam a `/restaurantes/[id]`. Para o
     visitante, o "Fazer um pedido" passa a rolar até `#restaurantes`, e não mais levar ao login.
     O usuário logado continua indo para `roleHome`;
   - **link direto** para `/loja/restaurantes/[id]`, que também funciona sem conta.
2. **Cardápio.** Abre direto, sem exigir CEP. No topo, uma faixa pede o CEP: "Informe seu CEP
   para ver a taxa e o tempo de entrega".
3. **CEP informado.**
   - Se a loja entrega na zona, a faixa vira um resumo, "Entrega em 45–55 min · taxa a partir de
     R$ 5,99 · CEP 60000-000", com a opção "trocar".
   - Se não entrega, aparece um aviso claro: "Esta loja ainda não entrega no CEP 00000-000". O
     cardápio continua visível, mas "Adicionar" fica desabilitado com esse motivo.
   - O CEP fica guardado no navegador.
4. **Ficha do prato.** É igual à de hoje: variações, adicionais com o erro já corrigido no item 4
   e observação.
5. **Carrinho** em `/loja/carrinho`. Mostra itens, quantidades, observações, subtotal, taxa e
   tempo estimados (pelo CEP) e o pedido mínimo da zona.
   - Sem CEP, pede o CEP ali mesmo. O CEP é **obrigatório** para continuar, e a cobertura é
     conferida antes de pedir a conta.
   - Logo acima do botão aparece a frase "Os valores são confirmados ao entrar na sua conta".
   - O botão é **"Continuar"** e leva a `/entrar?voltar=/loja/carrinho`. O texto da tela de
     entrar diz: "Entre ou crie sua conta para finalizar o pedido."
6. **Depois de entrar** (por email e senha, SMS, Google ou Facebook):
   1. o carrinho do navegador é enviado para a conta;
   2. o carrinho do navegador é apagado;
   3. a pessoa vai para `/loja/carrinho`.

   Lá ela escolhe ou cadastra o endereço completo, e o resto segue o fluxo atual (tipo de pedido,
   pagamento, cupom, gorjeta). O CEP guardado pré-preenche o cadastro do endereço.
7. **Cadastro novo.** O cadastro pede confirmação de email antes da senha (o fluxo já é assim).
   O carrinho continua no navegador. Quando a pessoa termina o cadastro e entra **no mesmo
   aparelho**, o passo 6 acontece normalmente.

## Decisões de comportamento

Estas decisões foram delegadas pelo dono do produto e tomadas por mim.

- **Conflito com carrinho já salvo na conta:** vale o carrinho que a pessoa acabou de montar. O
  da conta é substituído, e ela vê o aviso "Seu carrinho foi atualizado com os itens que você
  escolheu agora."
- **Uma loja por carrinho:** é a mesma regra e a mesma mensagem do cliente logado ("Um pedido pode
  reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual."). Não há
  pergunta nova de "trocar", nem para o visitante nem para o logado.
- **Cliente logado sem endereço ou fora da zona:** também vê o cardápio. Hoje,
  `restaurant-menu.tsx` esconde o cardápio nesses casos. Passa a ser um caminho só: a zona vem do
  endereço selecionado e, sem ele, do CEP guardado. Sem nenhum dos dois, o cardápio aparece sem
  zona. O "não entrega aqui" desabilita o "Adicionar" com o motivo, mas não esconde os pratos.
- **Validade do carrinho de visitante:** ele guarda a data da última alteração e vale por
  **7 dias**. Depois disso, é descartado na leitura e não é importado. Assim, um carrinho velho
  não substitui um carrinho mais novo da conta.
- **Import que não aproveita nada:** se nenhum item do visitante for válido no servidor, o
  carrinho da conta **não** é apagado. A pessoa vê "Os itens que você escolheu não estão mais
  disponíveis".
- **Limites:** são os mesmos do import do servidor, até 30 linhas e quantidade de 1 a 20 por
  linha. O visitante não consegue passar disso.
- **Itens que deixaram de valer** (produto indisponível, opção removida, loja diferente): o
  servidor descarta o item no import. Depois de entrar, a pessoa vê "N itens não estavam mais
  disponíveis e foram retirados".
- **Preço:** o carrinho do visitante calcula e mostra preços a partir dos dados públicos do
  catálogo (`/catalog/products/{id}`). Depois do import, o que vale é o snapshot do servidor.
  **Aviso novo** (não existe hoje): se o subtotal do servidor for diferente do que o visitante
  via, o site mostra "Os valores foram atualizados" com o subtotal novo. O 409 do checkout
  continua como proteção final.
- **Taxa estimada:** sem endereço completo, a taxa mostrada é a **menor taxa possível da zona**,
  com o rótulo "a partir de". Ela é calculada no servidor com a mesma regra de
  `DeliveryFee.compute`:
  - zona com taxa fixa: o valor fixo;
  - zona por km: a taxa base (ou a fixa, sem base) mais 1 km.

  O `/zones/resolve` passa a devolver esse valor (`minimum_fee_cents`). A taxa exata é calculada
  depois do endereço completo, como hoje.
- **Tempo estimado:** usa a mesma fórmula do site (`app/loja/eta.ts`: preparo da loja mais os
  minutos da zona, decisão de 29/10).
- **Retorno do login:** o parâmetro `voltar` só aceita caminho interno, que começa com `/` e não
  com `//`, sem esquema. Qualquer outro valor é ignorado e a pessoa vai para `roleHome`. Se não
  houver `voltar`, mas existir carrinho de visitante, o cliente vai para `/loja/carrinho`.
- **Login com outro papel** (loja, admin, entregador): o carrinho de visitante não é importado e
  continua no navegador. O destino é o `roleHome` desse papel.
- **Indicação:** o `captureReferralLink()` continua rodando para o visitante.

## O que já existe e onde mexer

**API (Java)**

- `CatalogController` (`/catalog`, `/catalog/meta`, `/catalog/products/{id}`, `/catalog/search`,
  `/catalog/tags`, `/zones/resolve`) não lê sessão, ou seja, já é público. Conferir num teste.
- `/catalog/search` e `/catalog/tags` **exigem `zoneId`**. **Mudança:** `zoneId` passa a ser
  opcional **quando `restaurantId` vem junto**. Sem zona, a busca não filtra por cobertura e
  devolve os produtos disponíveis da loja (loja ativa, produto ativo). Sem `restaurantId`, o
  `zoneId` continua obrigatório (400).
- `POST /cart/import` (`CartController.importLocal` → `CartService.importIfEmpty`) hoje só importa
  com o carrinho da conta vazio, e o `ImportItem` não tem observação. **Mudança:**
  - aceitar `replace: true` no corpo: valida os itens primeiro e, **só se ao menos um for válido**,
    limpa o carrinho da conta e importa, na mesma transação e com o mesmo `lock`;
  - aceitar `note` (até 140 caracteres) em cada item, gravada como a observação por item da
    PR #87. **Atenção:** desde a `V075`, a observação faz parte da chave do carrinho. A chave de
    duplicidade do import (`productId:variationId:addonKey`) tem de incluir a observação, senão
    duas linhas que só diferem nela perdem uma em silêncio;
  - devolver quantos itens foram descartados (`skipped`) junto com o snapshot.

  Sem `replace`, o comportamento atual fica igual, porque o legado do `localStorage`
  (`foodie-cart-v1:<id>`) continua usando.
- `/delivery/estimate` exige endereço salvo e **não muda**.

**Site (Next.js)**

- `app/loja/layout.tsx`: hoje redireciona todo visitante para `/entrar`. **Mudança:** em
  `/loja/restaurantes/[id]` e `/loja/carrinho`, renderizar sem usuário (modo visitante). As
  demais rotas de `/loja` continuam exigindo login. Usuário logado com papel diferente de cliente
  continua indo para `/painel`.
- `app/loja/customer-context.tsx` (654 linhas) concentra cardápio, carrinho, endereço e checkout.
  **Mudança:** um modo visitante no provider:
  - não chama nenhuma rota autenticada (`/cart*`, endereços, favoritos, notificações,
    `/cart/contact-phone`, `/delivery/estimate`, mesas, meios offline e configuração de
    pagamento);
  - a zona vem do CEP guardado (`/zones/resolve`), não do endereço selecionado;
  - o carrinho vem de um armazenamento local.

  A forma de organizar isso fica com quem constrói. Uma opção é extrair o carrinho para uma
  interface com duas implementações, servidor e local. O que importa é que a tela (`restaurant-menu.tsx`,
  `carrinho/page.tsx`) use a mesma interface nos dois modos.
- **Carrinho local:** fica no `localStorage`, na chave `foodie-cart-guest-v1`, com
  `{ restaurantId, items: [{ productId, variationId, addonIds, quantity, note, nome/preços para
  exibir }] }`. Toda leitura e escrita fica em `try/catch`: sem armazenamento, o carrinho vive só
  na memória da aba.
- **CEP do visitante:** fica no `localStorage`, na chave `foodie-guest-cep-v1`, guardando só os
  dígitos.
- `app/entrar/page.tsx`: hoje sempre manda para `roleHome`. **Mudança:** respeitar `voltar` com a
  validação acima. Mostrar a frase de finalizar pedido quando `voltar=/loja/carrinho`. Google e
  Facebook pegam o token na própria página e não redirecionam, então o `voltar` continua na URL.
  Para o SMS e para o login por email, aplicar o mesmo destino depois do sucesso.
- **Import depois do login:** no `useEffect` de carga do carrinho do `customer-context` (onde já
  existe a importação do legado), se houver `foodie-cart-guest-v1`, chamar `/cart/import` com
  `replace: true` antes de qualquer outra coisa. Depois, apagar a chave e mostrar os avisos de
  substituição ou de itens descartados.
- `app/page.tsx` (home): o "Fazer um pedido" do visitante aponta para `#restaurantes`.
- `app/restaurantes/[id]/page.tsx`: o botão aponta para `/loja/restaurantes/${id}`.

## Testes esperados

- **Java:**
  - busca e tags sem `zoneId` com `restaurantId`, que devolvem só itens ativos da loja;
  - busca sem os dois, que dá 400;
  - import com `replace` substituindo um carrinho existente;
  - `replace` com todos os itens inválidos, que mantém o carrinho da conta;
  - import com `note`, incluindo duas linhas que só diferem na observação;
  - `minimum_fee_cents` do `/zones/resolve` para zona fixa e por km;
  - contagem de `skipped`;
  - import sem `replace` mantendo o comportamento atual;
  - as rotas do catálogo respondendo sem cookie.
- **Site** (`node --test`, entrando no `verify.mjs`):
  - validação do `voltar` (aceita `/loja/carrinho`; recusa `//evil.com`, `https://…`,
    `javascript:` e vazio);
  - leitura e escrita do carrinho local, incluindo armazenamento indisponível, limites de 30
    linhas e 20 unidades, regra de uma loja e validade de 7 dias.
- **Conferência manual no staging, no celular**, de ponta a ponta:
  1. link da loja → CEP → item com opção obrigatória e observação → carrinho → Continuar;
  2. cadastro novo ou login → volta ao carrinho com os itens e a observação → pedido em dinheiro.

  Repetir com conta que já tinha carrinho e com login por SMS. No modo visitante, o console não
  pode mostrar nenhum 401.

## O que fica com quem constrói

- A organização interna do `customer-context` (extrair ou não, quantos arquivos).
- O visual da faixa do CEP e do aviso de "não entrega aqui", seguindo os componentes atuais
  (`Alert`, `ui-badge`) e os dois temas.
- O texto exato dos avisos, desde que diga o mesmo que os textos acima.
