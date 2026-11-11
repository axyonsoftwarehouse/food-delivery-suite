# Taxa de entrega por bairro, definida pela loja — desenho (09/11/2026)

Pré-requisito do S02 (`docs/SUGESTOES_MELHORIA_2026-11-09.md`). Para a loja se publicar sozinha,
ela precisa dizer onde entrega sem depender do admin. Hoje isso é impossível.

**Status: aprovado pelo dono em 09/11** (decisões da §8 respondidas, todas pela sugestão).
**PR 1 implementado em 09/11** (branch `feat/taxa-por-bairro`): migration `V080`, consulta de CEP,
taxa da loja no pedido, catálogo, site e admin. Falta o PR 2 (tela "Área de entrega" da loja e
junção de bairros duplicados no admin). Ver §9.

## 1. Como é hoje (lido no código em 09/11)

- **Zona** (`zones`, `V003`) é uma região global criada pelo admin, com nome, cidade, UF,
  **taxa, pedido mínimo e tempo** (`delivery_fee_cents`, `minimum_order_cents`,
  `delivery_minutes`) e, opcionalmente, taxa por km (`base_fee_cents`, `per_km_cents`, `V014`).
- A zona cobre **faixas de CEP** (`zone_postal_ranges`, `V006`), cadastradas no admin, sem
  sobreposição. `GET /zones/resolve?postalCode=` acha a zona do CEP (`PostalCoverageService`).
- A loja atende uma zona quando o admin cria o vínculo (`restaurant_zones`,
  `AdminController.java:102`). **A loja não consegue fazer isso sozinha.**
- O **endereço do cliente guarda a zona** (`addresses.zone_id`): o CEP precisa cair numa faixa
  até para salvar o endereço (`AddressController`). O pedido também grava a zona (`orders.zone_id`)
  e confere se o CEP ainda é daquela zona (`requireAddressZone`).
- Catálogo, etiquetas, cobertura do carrinho, CEP do visitante, painel e relatórios filtram por
  zona (`CatalogRepository`, `customer-context.tsx`, `delivery-status.ts`, `DashboardService`).

**Problemas:** (1) só o admin define a área; (2) a taxa é da região, não da loja: duas lojas no
mesmo lugar são obrigadas a cobrar o mesmo; (3) ninguém da operação sabe montar faixas de CEP.

## 2. Objetivo

A loja, no painel, lista os bairros onde entrega e, para cada um, a taxa, o tempo e (se quiser) o
pedido mínimo. O cliente informa o CEP e o sistema descobre o bairro sozinho.

## 3. A ideia central: a zona passa a ser o bairro, e o preço passa a ser da loja

Em vez de jogar fora a zona (que está em endereços, pedidos, catálogo e relatórios), muda o que ela
significa:

| | Hoje | Depois |
| --- | --- | --- |
| O que é uma zona | Região do admin, com faixas de CEP | **Um bairro** de uma cidade (`cidade + UF + bairro`) |
| Quem cria | O admin | **O sistema**, quando um CEP é consultado ou uma loja cadastra o bairro |
| Onde fica a taxa | Na zona | **No vínculo loja × bairro** (`restaurant_zones`) |
| Como o CEP vira zona | Faixa de CEP | Consulta de CEP (bairro, cidade, UF), com cache local |

Assim `addresses.zone_id`, `orders.zone_id`, a cobertura do catálogo e os relatórios por zona
continuam funcionando, agora por bairro, sem reescrever o pedido.

## 4. Desenho

### 4.1 Banco (uma migration)

- `zones` ganha `neighborhood_key` (nome sem acento, minúsculo, espaços únicos) e um índice único
  em (`state`, `city_key`, `neighborhood_key`). As colunas de taxa da zona deixam de ser usadas
  (ficam no banco por compatibilidade; ver §8, decisão 3).
- `restaurant_zones` ganha `fee_cents`, `minimum_order_cents`, `delivery_minutes`, `active` e
  `created_at`. A migration copia para cada vínculo atual os valores da zona dele, para nada mudar
  de preço no dia da troca.
- `postal_code_cache` (CEP → zona, cidade, UF, bairro, data da consulta): evita consultar o
  serviço externo duas vezes para o mesmo CEP.
- `zone_postal_ranges` deixa de ser lida (ver §8, decisão 3).

### 4.2 CEP → bairro

- Serviço `PostalLookupService`: consulta o **ViaCEP** e, se falhar, a **BrasilAPI** (ambos
  gratuitos e sem chave). Prazo curto por chamada (o projeto já tem o padrão de timeouts externos).
  Resultado vai para `postal_code_cache`.
- Com bairro: acha ou cria a zona (`cidade + UF + bairro`).
- **CEP sem bairro** (cidade pequena com CEP único): responde a cidade e a lista de bairros
  daquela cidade que alguma loja atende. O cliente escolhe o dele.
- Serviço fora do ar e CEP fora do cache: mensagem "Não conseguimos consultar o CEP agora. Tente de
  novo em instantes", sem quebrar a tela.
- `GET /zones/resolve` mantém o endereço e passa a responder `{ zone, city, state, neighborhood,
  choices? }`. Limite por IP, porque agora uma rota pública chama um serviço externo.

### 4.3 Painel da loja: "Área de entrega"

- Tela nova em `/painel/entrega` (permissão `settings.manage`): lista dos bairros atendidos, cada
  um com taxa, tempo e pedido mínimo; adicionar, editar, pausar e remover.
- Ao adicionar, a loja digita o nome do bairro da **cidade dela**, com sugestões dos bairros já
  conhecidos nessa cidade (vindos das consultas de CEP e de outras lojas). Isso evita "Aldeota" e
  "Aldeôta" como bairros diferentes; a chave sem acento resolve o resto.
- Ação de conveniência: "aplicar a mesma taxa a vários bairros".
- A cidade da loja vem do endereço dela (o S02 e o S03 passam a exigir endereço da própria loja).

### 4.4 Cliente e visitante

- A cobertura (`catalog.coverage`) passa a trazer, por loja e bairro, a taxa, o tempo e o mínimo.
- O carrinho e o cardápio mostram a taxa **daquela loja** no bairro do cliente (hoje mostram a da
  zona). `deliveryStatus` (`delivery-status.ts`) continua com o mesmo contrato.
- Endereço salvo de bairro não atendido pela loja: "Esta loja não entrega no seu bairro", como hoje.
- O pedido calcula a taxa pelo vínculo loja × bairro e grava a zona (o bairro) no pedido.

### 4.5 Admin

- O painel de zonas vira **leitura e correção**: ver os bairros por cidade, renomear e **juntar
  duplicados** (move vínculos e endereços para o bairro certo). O admin não cria mais faixas de CEP.

### 4.6 Seed, smokes e testes

- O seed cria bairros e vínculos com taxa para a loja demo; o `smoke:coverage` passa a cobrir
  CEP → bairro → loja atende → taxa da loja. A consulta externa é simulada nos testes (sem rede).
- Testes de unidade: normalização do nome, cache, CEP sem bairro, serviço fora do ar, taxa por loja
  no pedido, junção de duplicados no admin.

## 5. Fora do escopo

- Raio em km e taxa por distância (ver §8, decisão 2).
- Desenhar a área num mapa.
- Lista oficial de todos os bairros do Brasil (os bairros nascem das consultas e das lojas).
- A home com CEP (S08) — ela usará esta mesma consulta quando for feita.

## 6. Anti-objetivos

- Mudar o preço de algum pedido ou loja no dia da migração.
- Uma rota pública que vire proxy aberto do ViaCEP (sem limite e sem cache).
- O cliente digitar o bairro livremente quando o CEP já diz qual é.

## 7. Riscos

- **Nome de bairro diferente entre o ViaCEP e o que a loja digitou** ("Edson Queiroz" × "Edson
  Queiróz"): a chave sem acento e as sugestões resolvem a maioria; o resto o admin junta (§4.5).
- **Dependência de serviço externo** para o primeiro uso de cada CEP: duas fontes e cache local.
- **Endereços de staging** salvos em zonas antigas: ao pedir, o sistema já responde "A cobertura
  deste endereço mudou. Cadastre o endereço novamente" (`requireAddressZone`). Não há produção ainda.
- Tamanho: **G** (estimativa). Sugestão de fatiar em dois PRs: (1) banco, CEP → bairro e taxa por
  loja, com o admin ainda cadastrando os vínculos; (2) tela da loja e a correção no admin.

## 8. Decisões do dono

Respondidas pelo Werner em 09/11: (1) CEP sem bairro → o cliente escolhe na lista; bairro do CEP
não atendido → **não pode trocar**; (2) taxa por km **sai** do fluxo; (3) faixas de CEP
**aposentadas**, zonas atuais viram bairros com as taxas nos vínculos; (4) pedido mínimo **por
bairro, com padrão da loja**. O texto abaixo é o que foi perguntado.

1. **CEP sem bairro e bairro fora da lista.** Quando o CEP não traz bairro, o cliente escolhe o
   bairro numa lista. *Sugestão: sim.* Quando o CEP traz um bairro que a loja não atende, o cliente
   **não** pode trocar por outro (evita escolher o bairro mais barato). *Sugestão: não pode.*
2. **Taxa por km.** Hoje existe (zona com base + por km). *Sugestão: sair do fluxo novo;* a loja
   que quiser cobrar mais longe põe taxa maior nos bairros distantes.
3. **Faixas de CEP antigas.** *Sugestão: aposentar* (só existem no staging) e transformar as zonas
   atuais em bairros com o mesmo nome, mantendo as taxas nos vínculos.
4. **Pedido mínimo por bairro ou por loja.** *Sugestão: por bairro, com um valor padrão da loja*
   que já vem preenchido.

## 9. O que o PR 1 entregou (09/11)

- **Banco (`V080`):** chaves do bairro em `zones` com índice único; taxa, mínimo, tempo e `active`
  em `restaurant_zones`, copiados das zonas; tabela `postal_code_cache`. `zone_postal_ranges` fica
  no banco, mas não é mais lida. Zonas antigas com o mesmo nome na mesma cidade ganham sufixo `#id`.
- **CEP → bairro:** `PostalDirectory` (ViaCEP, depois BrasilAPI, prazo de 3 s + 4 s),
  `PostalCoverageService` (cache de 180 dias, bairro criado na primeira consulta, CEP sem bairro com
  `choices`) e `PostalLookupLimiter` (20 consultas externas por IP a cada 10 minutos; o cache não conta).
  Respostas: 404 "CEP não encontrado", 503 "Não conseguimos consultar o CEP agora", 429.
- **Endereço:** só nasce em bairro que alguma loja atende (404 "Ainda não entregamos no seu bairro"),
  como antes acontecia com a faixa de CEP. Com CEP sem bairro, exige `zoneId` entre as opções.
- **Pedido e estimativa:** taxa, mínimo e tempo vêm de `restaurant_zones`; a taxa por km saiu.
- **Catálogo:** `coverage` traz `fee_cents`, `minimum_order_cents` e `delivery_minutes`; `/zones`
  lista os bairros atendidos, sem preço.
- **Admin:** `POST /admin/zones` cria o bairro (reaproveita grafia diferente), `GET /admin/zones`
  lista todos, `POST /admin/coverage` grava a taxa da loja no bairro e `DELETE /admin/coverage`
  remove. Saíram as rotas de faixa de CEP e `PATCH /admin/zones/{id}/pricing`. O painel de zonas foi
  refeito para esse fluxo.
- **Site:** cardápio, carrinho, lista de lojas e faixa do CEP mostram a taxa **da loja**; o visitante
  e o formulário de endereço escolhem o bairro quando o CEP não traz um.
- **Seed e smokes:** a zona demo vira o bairro "Centro"; os CEPs fictícios 60000001 a 60000004 ficam
  no cache (os testes não consultam o ViaCEP); o `smoke:coverage` foi reescrito para bairros.

**Ao publicar no staging:** rodar o seed uma vez (`deploy/README.md`) para gravar os CEPs de
demonstração no cache. Sem isso, os endereços demo (CEP 60000001, fictício) vão ao ViaCEP e falham.
Clientes reais em CEPs reais passam a cair em bairros novos, que precisam ser ligados às lojas pelo
admin até o PR 2.
