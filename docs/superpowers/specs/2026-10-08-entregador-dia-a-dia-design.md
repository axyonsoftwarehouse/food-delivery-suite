# Área do entregador — parte A, o dia a dia na rua: especificação

Data: 08/10/2026. Status: **aprovada** pelo dono do produto (desenho apresentado em quatro seções, todas
aprovadas em 08/10).
Origem: pedido de 08/10/2026 para deixar a área do entregador "realmente premium, bem fortalecida", depois
da decisão do mesmo dia de que **o entregador é exclusivo de uma loja** (PR #16).

## Roteiro da área do entregador

O trabalho foi dividido em quatro partes, nesta ordem, cada uma com especificação e PR próprias:

| Parte | Tema | Estado |
| --- | --- | --- |
| **A** | O dia a dia na rua: tela própria para o celular, rota, contato, localização | **esta especificação** |
| B | Confiança na entrega: código de confirmação ou foto, falha com motivo e foto | a especificar |
| C | Motivação e carreira: nota, avaliações, metas, ganhos com gráfico e período | a especificar |
| D | Gestão pela loja: mapa dos entregadores, "Estou disponível", turnos, despacho pelo mais próximo | a especificar |

Formato decidido: **web pensada para o celular primeiro** (instalável como PWA); o app nativo (Expo, como o
da cozinha) fica para depois, quando o rastreio em segundo plano fizer falta de verdade.

## Ponto de partida (08/10)

- O entregador usa o `/painel` genérico da loja e do admin, feito para computador: *Visão geral* (sem nada
  dele), *Minhas entregas* (colunas "A retirar" / "Em rota"), *Ganhos* (extrato informativo) e
  *Configurações*.
- **Rastreio quebrado na prática:** o cliente lê a posição do entregador (`GET /orders/{id}/tracking`), mas
  nenhuma tela envia a posição — `POST /courier/location` existe e não é usado.
- Sem rota (o endereço é só texto), sem contato com cliente ou loja, sem aviso de entrega nova com a tela
  aberta.
- Telefone: o do cliente (`users.phone`) é opcional e serve ao login por SMS; **a loja não tem telefone**.
- A loja tem `address_text`, `latitude` e `longitude` (só o admin edita); o endereço do cliente tem
  coordenadas. A distância do pedido (`orders.distance_meters`) já é gravada.

## Decisões (08/10)

1. **Área própria `/entregas`**, no padrão da área do cliente (`/loja`): layout próprio para o celular, menu
   inferior, CSS próprio. Entregador que acessa `/painel` vai para `/entregas`.
2. **Contato por ligação e WhatsApp, com o número só durante a entrega.**
3. **Telefone de contato no pedido de entrega** (obrigatório no checkout de entrega).
4. **Localização só durante a entrega**, automática; a regra vale também no servidor.
5. **Rota por link** para o Google Maps ou o Waze do celular — sem API paga. O Mapbox segue só no servidor
   (geocodificação e distância); um mapa desenhado na tela fica para a parte D.

## Telas

**Casca:** topo com o nome, o indicador da localização e o botão de sair; menu inferior com
**Agora · Entregas · Ganhos · Perfil**.

**Agora (principal)**
- **Entrega da vez em destaque:** a que já está em rota; se não houver, a mais antiga atribuída. Indicador
  de duas etapas: **Retirar na loja → Entregar ao cliente**.
- No cartão:
  - endereço da etapa em letra grande, com a distância aproximada;
  - **Rota** (Google Maps ou Waze, já com o destino);
  - **Ligar** e **WhatsApp**: a loja na retirada, o cliente na entrega — os dois contatos ficam acessíveis
    nas duas etapas;
  - pagamento em destaque: "Receber R$ 45,90 em dinheiro · troco para R$ 50" ou "Já pago online";
  - itens do pedido, recolhidos.
- Ação principal grande: **Retirei o pedido** ou **Entreguei** (com valor a receber, abre antes a
  confirmação do recebimento). Secundária: **Não consegui entregar**, com motivo.
- Mais de uma entrega: as próximas numa fila abaixo.
- Sem entrega: "Nenhuma entrega agora" e o resumo do dia (entregas e quanto ganhou).

**Entregas:** histórico de hoje e da semana, com o status de cada uma.

**Ganhos:** o extrato que já existe, adaptado ao celular (a parte C enriquece).

**Perfil:** dados, loja à qual está ligado, veículo; situação das permissões de localização e notificação,
com o caminho para liberar; **Instalar na tela inicial**.

## Dados (migração `V064`)

- **`orders.contact_phone`** (`VARCHAR(20)`, nulo): telefone de contato do pedido de entrega.
  - Checkout de entrega: **obrigatório**, pré-preenchido com `users.phone` se houver, validado como telefone
    brasileiro com DDD (10 ou 11 dígitos, guardado só com dígitos).
  - Pedido recorrente: copia o `contact_phone` do último pedido de entrega do cliente (pode ficar nulo).
  - Retirada, consumo no local e PDV: não pedem.
- **`restaurants.phone`** (`VARCHAR(20)`, nulo): telefone da loja, editado pela própria loja em
  **Minha página**, com a mesma validação. Sem ele, o botão "Ligar para a loja" não aparece.

## API

**Nova, só para o papel `courier`:**
- `GET /courier/deliveries/active` — entregas atribuídas ou em rota **do próprio entregador**, com: número,
  status e horário; loja (nome, endereço, coordenadas, telefone); cliente (nome, endereço completo com
  complemento, coordenadas, **telefone de contato**); distância; itens; forma e status do pagamento, valor a
  receber e troco.
- `GET /courier/deliveries/history?period=today|week` — entregas encerradas, **sem telefone**.
- **Os telefones só saem por `/active`**, cujo filtro é "atribuído a mim e em andamento": quando a entrega
  termina, o número deixa de aparecer.

**Reaproveitada:**
- Ações: `PATCH /orders/{id}/status` (retirar, entregar, falha) e `PATCH /orders/{id}/payment`
  (recebimento) — nenhuma regra muda.
- Resumo do dia e ganhos: `/me/earnings` e `/me/earnings/ledger` com filtro de data.
- **Localização:** `POST /courier/location` passa a aceitar só quando o entregador tem entrega ativa; fora
  disso, **409**.
- Telefone da loja: na rota da loja que edita **Minha página** (`StorefrontController`).
- Checkout: `POST /cart/checkout` ganha `contactPhone`.

**Links de rota** (montados na tela, sem API):
- Google Maps: `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>`
- Waze: `https://waze.com/ul?ll=<lat>,<lng>&navigate=yes`
- Sem coordenadas, os dois usam o endereço em texto.

## Localização e avisos

- **Envio automático** com entrega atribuída ou em rota e a tela aberta: a cada **15 s**, ou antes se andar
  mais de **30 m**. Para quando a última entrega termina e logo após **Entreguei** ou
  **Não consegui entregar**.
- Tela fechada ou celular bloqueado: o navegador pausa; ao voltar, retoma sozinho.
- **Indicador no topo:** *Compartilhando* · *Pausado (sem entrega)* · *Bloqueado* (um toque explica como
  liberar no Chrome e no Safari).
- **Sem sinal:** guarda só a última posição e envia quando a conexão volta; não acumula histórico.
- **Permissões no momento certo:** localização na primeira entrega ativa; notificação no primeiro acesso, com
  o motivo explicado.
- **Tela acesa** durante a entrega ativa (Wake Lock), quando o navegador oferece.
- **Entrega nova:** com a tela aberta, a lista atualiza sozinha e, quando chega entrega nova, som curto e
  vibração; com a tela fechada, a notificação push "entrega atribuída" (já existe) abre a tela "Agora".

## Erros e casos de borda

- **Entregador trocado ou removido** com a tela aberta: a entrega some de "Agora" na próxima atualização,
  com "Esta entrega foi passada para outro entregador"; a localização para se não sobrar entrega.
- **Pedido cancelado durante a entrega:** "Pedido cancelado pela loja — não entregue", e a entrega sai da
  fila.
- **Ação recusada pelo servidor** (pagamento pendente, transição que não vale mais): a mensagem do servidor
  aparece no cartão e a entrega é recarregada.
- **Toque duplo:** o botão trava enquanto a ação está em andamento.
- **Sem conexão:** faixa "Sem conexão — tentando de novo"; ações desabilitadas até voltar. Não há fila de
  ações offline.
- **Sem telefone** (pedido antigo ou loja sem telefone): somem Ligar e WhatsApp; aparece "Telefone não
  informado".
- **Sem coordenadas:** a rota abre pelo endereço em texto.
- **Navegador sem geolocalização, push ou Wake Lock:** a área funciona sem aquele recurso, com a explicação
  no Perfil.

## Segurança

- Todas as rotas `/courier/*` exigem o papel `courier` e filtram pelo próprio entregador; entrega de outro
  responde **404**.
- O telefone de contato não aparece em nenhuma rota do entregador fora de `/active`, nem em entrega
  encerrada.

## Fora de escopo (partes seguintes)

- Código de confirmação, foto do comprovante, foto da falha (parte B).
- Nota, avaliações, metas, ganhos com gráfico (parte C).
- "Estou disponível", turnos pelo próprio entregador, mapa da loja, despacho pelo mais próximo, posição fora
  da entrega (parte D).
- Chat dentro do app (pendência própria do backlog) e app nativo com rastreio em segundo plano.

## Testes

- **Unidade (Java):** `/active` só traz as entregas do entregador e em andamento, com os telefones;
  `/history` sem telefone; localização aceita com entrega ativa e **409** sem ela; checkout de entrega sem
  telefone ou com telefone inválido → **400**, retirada sem telefone aceita; recorrência copia o contato do
  último pedido de entrega; telefone da loja editável só pela própria loja.
- **Migração `V064`** num MariaDB de verdade (`VERIFY_INTEGRATION=1 pnpm verify`).
- **Smoke:** o fluxo completo confere `/courier/deliveries/active` com o telefone durante a entrega e sem ele
  depois, e a localização aceita durante a entrega e recusada depois.
- **Tela no navegador (celular):** entrega da vez, rota, contatos, confirmação do pagamento,
  **Retirei → Entreguei**, fila com duas entregas, estado vazio, localização negada, tema claro e escuro.
