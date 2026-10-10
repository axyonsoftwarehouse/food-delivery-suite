# Área do entregador — parte D, gestão pela loja: especificação

Data: 10/10/2026. Status: **aprovada** pelo dono do produto em 10/10/2026 (desenho em cinco seções e este
texto).
Origem: roteiro da área do entregador (partes A, B e C em `2026-10-08-entregador-dia-a-dia-design.md`,
`2026-10-09-entregador-confianca-design.md` e `2026-10-09-entregador-motivacao-design.md`, PRs #21, #22 e #23).

## Decisões (10/10)

1. **O problema central é a loja saber a quem atribuir.** O núcleo é "Estou disponível" e a sugestão do
   entregador mais perto no despacho; o mapa é apoio visual. Não há atribuição automática.
2. **A posição só é compartilhada enquanto o entregador está disponível** (em turno) ou com entrega em mãos
   (regra da parte A). Fora do turno e sem entrega, nada é enviado e a API recusa (409).
3. **Turno é registro de jornada, não escala planejada.** "Estou disponível" abre um turno e "Encerrar" fecha,
   na `courier_shifts` que já existe. A loja vê quem está em turno e o histórico de horas.
4. **Mapa: Leaflet + OpenStreetMap.** Sem chave, sem conta e sem custo; blocos dos servidores do OSM (uso leve,
   com a atribuição visível). Trocar de provedor de blocos é trocar uma URL.
5. **Quem está fora do turno pode receber pedido, com aviso.** Disponíveis aparecem primeiro, pela distância;
   fora do turno fica por último e marcado. O servidor **não** recusa a atribuição por causa do turno.
6. **Abordagem técnica: consulta periódica.** O entregador envia a posição a cada ~30 s; a loja consulta um
   quadro a cada ~10 s. Distância em linha reta (haversine), calculada no servidor. Sem push em tempo real e sem
   distância por rota (Mapbox ou OSRM) — ficam como evolução.

## Ponto de partida (10/10)

- `courier_locations` (V047): **uma linha por entregador**, só a última posição (`latitude`, `longitude`,
  `updated_at`). Gravada por `POST /courier/location` (`TrackingController`), que hoje só aceita **durante a
  entrega** (409 fora dela — parte A).
- `courier_shifts` (V041): `courier_id`, `started_at`, `ended_at`. Usada só por rotas do admin
  (`CourierAdminController`: listar, iniciar, encerrar), **sem tela** e sem ligação com o resto. Nada impede
  dois turnos abertos.
- `restaurants.latitude/longitude` e `addresses.latitude/longitude` (V014) existem. `HaversineRoutingProvider`
  já calcula distância em linha reta (reserva do Mapbox no frete).
- Despacho: no painel, o cartão do pedido pronto tem um seletor com os entregadores aprovados e não suspensos da
  loja e o botão "Atribuir" (`PATCH /orders/{id}/status`, ação `assign`, permissão `orders.dispatch`). O servidor
  só exige que o entregador seja da loja, aprovado e não suspenso. **Não há limite** de entregas simultâneas.
- Na área `/entregas`, o cabeçalho mostra o estado da localização (`useLocationStatus`: "Compartilhando",
  "Pausado (sem entrega)", "Localização bloqueada", "Sem localização").
- Tarefas agendadas seguem o padrão do `OrderExpiryRunner` (PR #18).
- Suspensão: `PATCH /restaurant/couriers/{id}/suspension` (loja) e `PATCH /admin/couriers/{id}/suspension`
  (admin).

## Dados (migration `V067`)

- `courier_shifts` ganha `restaurant_id BIGINT UNSIGNED NULL` (FK para `restaurants`), gravado na abertura com a
  loja do entregador. O histórico fica com a loja em que o turno aconteceu, mesmo que o entregador seja ligado a
  outra loja depois. Turnos antigos ficam com `NULL` e não aparecem para nenhuma loja.
- **Um turno aberto por entregador**, garantido pelo banco: coluna gerada
  `open_courier_id = IF(ended_at IS NULL, courier_id, NULL)` com índice único. Se o staging tiver dois turnos
  abertos do mesmo entregador antes da migration, ela fecha os mais antigos (`ended_at = started_at`) antes de
  criar o índice.
- Índice `(restaurant_id, started_at)` para o histórico da loja.
- `courier_locations` não muda: continua só a última posição, sem histórico de trajeto.

## Regras

1. **Abrir o turno** ("Estou disponível"): só entregador com loja, aprovado e não suspenso (senão 409). Se já
   houver turno aberto, devolve o existente (idempotente).
2. **Encerrar**: fecha o turno aberto. Sem entrega ativa (pedido `assigned` ou `picked_up` dele), **apaga a
   linha de `courier_locations`**. Com entrega ativa, a posição continua sendo aceita por causa da entrega e a
   resposta avisa (`keepsSharing: true`). Encerrar sem turno aberto: 200 sem efeito.
3. **Envio de posição** (`POST /courier/location`): aceito com turno aberto **ou** entrega ativa; fora disso,
   409 (como hoje).
4. **Status para a loja**, calculado no servidor (`CourierStatus`, função pura):
   - `delivering` — tem entrega ativa (vale mesmo fora do turno); `activeDeliveries` = quantas;
   - `available` — turno aberto, sem entrega ativa, posição com até **2 minutos**;
   - `no_signal` — turno aberto, sem entrega ativa, sem posição ou posição com mais de 2 minutos;
   - `off_shift` — sem turno aberto e sem entrega ativa.
5. **Coordenadas e distância só com posição recente** (até 2 minutos) e status `available` ou `delivering`. Uma
   posição velha nunca é mostrada como atual.
6. **Distância** = haversine da última posição até `restaurants.latitude/longitude`. Loja sem coordenadas:
   `distanceMeters = null` e a ordem dentro do grupo cai para o nome.
7. **Ordem do quadro**: `available` pela distância (sem distância por último), depois `delivering` pela
   distância, depois `no_signal`, depois `off_shift`; empate pelo nome. **`suggested: true` só no primeiro
   `available`**; se não houver, ninguém é sugerido.
8. **Turno esquecido**: tarefa agendada (a cada 5 min, padrão do `OrderExpiryRunner`) fecha turnos abertos há
   mais de **12 horas** (`ended_at = started_at + 12 h`) e apaga a posição de quem ficou sem turno e sem entrega.
9. **Suspensão** (pela loja ou pelo admin) fecha o turno aberto e apaga a posição.
10. **Horas**: duração = `ended_at − started_at`; turno aberto conta até agora. O período (7 ou 30 dias) conta
    turnos iniciados nele.
11. O quadro e as horas só mostram entregadores **da loja** e não suspensos (o histórico de horas de um
    suspenso continua acessível pela Equipe).

## API

**Entregador** (papel `courier`):

- `GET /courier/shift` — `{ open: true, startedAt }` ou `{ open: false }`.
- `POST /courier/shift` — abre o turno: **201** `{ open: true, startedAt }`; com turno já aberto, **200** com o
  existente; sem loja, não aprovado ou suspenso: **409**.
- `DELETE /courier/shift` — encerra: `{ ok: true, keepsSharing }`.
- `POST /courier/location` (existente) — passa a aceitar com turno aberto ou entrega ativa; 409 fora disso.

**Loja** (papéis `restaurant`/`kitchen`, permissões da loja):

- `GET /restaurant/couriers/board` — permissão `orders.dispatch`:
  `{ restaurant: { latitude, longitude }, couriers: [{ id, name, status, activeDeliveries, shiftStartedAt,
  latitude, longitude, locationUpdatedAt, distanceMeters, suggested }] }`, na ordem da regra 7. Coordenadas,
  `locationUpdatedAt` e `distanceMeters` são `null` quando a regra 5 não permite.
- `GET /restaurant/couriers/{id}/shifts?days=7|30` — permissão `couriers.manage`:
  `{ totalMinutes, shifts: [{ startedAt, endedAt, minutes }] }` (mais recente primeiro). Entregador de outra loja:
  404; `days` diferente de 7 ou 30: 400.

**Ajustes no que já existe:**

- `POST /admin/couriers/{id}/shifts` grava o `restaurant_id` do entregador (nulo se ele estiver sem loja; esse
  turno não aparece para nenhuma loja) e respeita o turno único (com turno aberto, devolve o existente).
- As duas rotas de suspensão aplicam a regra 9.
- Contrato do `api-client` regerado.

Não há rota de "atribuir o mais próximo": a sugestão vem no quadro e a atribuição continua sendo o `assign`.

## Telas do entregador (`/entregas`)

- **Agora, fora do turno e sem entrega:** cartão "Você está fora do turno. A loja só vê sua posição enquanto
  você estiver disponível." com o botão principal **"Estou disponível"**. O toque também pede a permissão de
  localização (o navegador exige gesto). Negada, o turno abre assim mesmo e aparece o aviso fixo "Sem
  localização: a loja não vê sua distância. Libere a localização nas permissões do site."
- **Em turno:** faixa no topo "Disponível desde 14:05" com **"Encerrar turno"**. A confirmação é **na própria
  tela** ("Confirmar encerramento" / "Voltar"), **sem `window.confirm`**. Com entrega em mãos, avisa "Você ainda
  tem 2 entregas; a localização continua até terminar."
- **Cabeçalho:** o indicador passa a mostrar **Fora do turno**, **Disponível**, **Em entrega** ou **Sem
  localização** (GPS negado ou sem suporte), e continua levando ao Perfil.
- **Envio:** em turno ou com entrega, acompanha o GPS e envia **no máximo a cada 30 s**, ou antes se andou mais
  de 50 m. Se o envio der 409 (turno fechado pela tarefa ou por suspensão), para de acompanhar e volta a "Fora do
  turno".
- **Limite conhecido:** com a tela apagada ou o navegador em segundo plano, o site para de enviar a posição e a
  loja vê "Sem sinal" depois de 2 minutos. Só o app nativo (adiado na parte A) resolveria.

## Telas da loja (painel)

- **Despacho (Pedidos):** no cartão do pedido de entrega pronto (ou já atribuído, para troca):
  - sugestão em uma linha — "Mais perto: Ana · Disponível · 1,2 km" — com **"Atribuir a Ana"** (um toque, ação
    `assign`);
  - **"Outro entregador"**: seletor com todos na ordem do quadro e o status em cada opção ("Bruno · Em entrega (1)
    · 3,4 km", "Caio · Sem sinal", "Davi · Fora do turno"); escolher alguém fora do turno mostra "Fora do turno:
    ele pode não ver o pedido agora";
  - sem ninguém disponível: "Nenhum entregador disponível agora" e só o seletor;
  - o quadro é consultado a cada 10 s e para com a aba escondida; se falhar, o cartão volta ao seletor simples
    de hoje (a atribuição nunca fica bloqueada). A visão do admin não muda.
- **Página nova "Entregadores"** no menu da loja (só com `orders.dispatch`): mapa Leaflet com blocos do OSM e a
  atribuição "© OpenStreetMap" visível; marcadores da loja e dos entregadores com posição, cor pelo status;
  tocar num entregador mostra nome, status, distância e "atualizado há 40 s". Abaixo, a lista do quadro (os "Sem
  sinal" e "Fora do turno" só aparecem nela). Enquadra a loja e os entregadores visíveis. Loja sem coordenadas:
  "Cadastre o endereço da loja para ver o mapa", com link para Configurações. Blocos sem carregar: a lista segue
  e aparece "Mapa indisponível agora". No celular, mapa em metade da tela e lista embaixo. Leaflet carregado só
  nessa página, no navegador; o mapa fica claro nos dois temas (os blocos do OSM não têm versão escura).
- **Equipe:** em "Ver desempenho" (parte C), a linha "Em turno: 12 h 30 min em 7 dias · 48 h em 30 dias" e os
  últimos turnos (início, fim, duração).

## Testes

- **Java, funções puras:** `CourierStatus` (os quatro estados e o limite de 2 min), ordem do quadro e
  `suggested`, haversine com e sem coordenadas da loja, soma de horas com turno aberto.
- **Java, serviços e controllers:** abrir idempotente e 409 sem loja/aprovação/suspenso; encerrar apaga ou
  mantém a posição; posição aceita em turno, aceita em entrega, 409 fora; quadro só da própria loja, sem
  suspensos, sem coordenadas velhas; tarefa das 12 h; suspensão fecha o turno (loja e admin); turnos de outra loja
  404; `days` inválido 400; permissões `orders.dispatch` e `couriers.manage`; turno do admin com `restaurant_id`.
- **Smoke (`VERIFY_INTEGRATION`):** entregador abre o turno e envia posição; a loja vê `available` com distância
  e `suggested`; atribui; ele vira `delivering`; encerra com entrega (`keepsSharing: true`); depois de entregar,
  a posição dá 409; horas da loja com 1 turno.
- **Web:** `tsc --noEmit` e `npm run build`.
- **Roteiro de telas no staging** (celular, claro e escuro): abrir e encerrar o turno, GPS negado, sugestão e
  atribuição em um toque, mapa, Equipe com horas; console limpo.

## Publicação

- Uma migration (`V067`); dado existente só ganha `restaurant_id` nulo nos turnos antigos (e, se houver, turnos
  duplicados abertos são fechados).
- **Nada muda para a loja até alguém tocar "Estou disponível":** sem turnos, todos aparecem "Fora do turno" e o
  despacho funciona como hoje.
- Dependência nova na web: `leaflet` (versão fixada) e os tipos dela.

## Fora desta parte

- Escala planejada (quem trabalha quando), pausas dentro do turno e histórico de horas para o próprio
  entregador.
- Atribuição automática, push em tempo real, distância por rota e pedidos ou rotas desenhados no mapa.
- Alerta de "ninguém disponível" e exportação das horas.
- Envio de posição com a tela apagada (exige app nativo).
