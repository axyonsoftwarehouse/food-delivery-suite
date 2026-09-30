# Ideias futuras — Foodie

Documento de memória. Última atualização: 29/09/2026.
**Não é backlog. Não é compromisso.** É onde ideias ficam guardadas para não
se perderem. Quando uma ideia amadurecer, vira épico em
`PLANO_EPICOS_STACKFOOD.md` com número `E##`.

## Como usar

- **Ideia nova?** Adicione na seção "Ideias soltas", sem estrutura.
- **Ideia amadureceu?** Promova para uma seção com "O que é / Por quê / Quando".
- **Ideia morreu?** Marque como `❌ Descartada` com data e motivo. Não apague —
  o histórico evita que a mesma ideia seja discutida três vezes.

> **Nota sobre numeração.** O `PLANO_EPICOS_STACKFOOD.md` vai até **E47**. Os
> números seguintes (E48 em diante) **ainda não existem no plano** — precisam ser
> registrados lá antes de serem tratados como épico. O "modo suporte" do admin,
> hoje eleito próximo passo em `ESTADO_ATUAL.md`, é o primeiro candidato a E48.

---

## Módulos de gestão para o lojista

**Status:** ideia em amadurecimento. Sem decisão.

**O que é:** expandir a plataforma além do delivery, oferecendo ao lojista
ferramentas de gestão que hoje ele resolve fora:

1. **Financeiro robusto** — fluxo de caixa, contas a pagar/receber,
   conciliação, área contábil, relatórios fiscais
2. **Controle de estoque** — insumos, fichas técnicas, baixa automática por
   venda, alertas de reposição
3. **Fornecedores** — cadastro, cotações, pedidos de compra, histórico
4. **Área de RH** — funcionários, escalas, ponto, folha simplificada
5. **Marketing / CRM** — segmentação de clientes, campanhas automatizadas,
   funil de recompra, NPS

**Por quê:** aumenta o valor percebido da assinatura, diferencia a Foodie de
concorrentes focados só em delivery, aumenta o custo de troca (o lojista fica
preso ao ecossistema).

**Quando:** **depois** que a plataforma estiver comercialmente operável
(cobrança real da assinatura, deploy estável, modo suporte implementado).

**Como fazer:** cada bloco vira um ou mais épicos, provavelmente em uma nova
"Onda 5" ou "Onda 6". Avaliar antes se faz sentido como **módulo pago** dentro
da assinatura.

**Atenção:** parte disso já existe embrionariamente na plataforma — há telas de
`estoque` e `financeiro` no painel do restaurante. O escopo aqui é o que está
**além** do delivery, não duplicar o que já foi entregue.

---

## Destino do material de referência do legado

**Status:** decisão pendente. **O trabalho pesado já foi feito.**

**O que é:** decidir o que fazer com o que restou do StackFood v9.

**O que já está resolvido** (não refazer):

- O código do legado **foi removido** do repositório em 25/09/2026
  (`04e5686`), não apenas desativado. Não existem mais `admin-panel`, `web`,
  `app-*` nem `payment-gateway` na árvore.
- O snapshot completo está preservado na **tag `legacy-stackfood-v9`**
  (`git show legacy-stackfood-v9:caminho/do/arquivo`).
- Os apps Flutter ficaram em `reference/flutter-apps/` como material de
  consulta, fora do build (`reference/README.md`).
- Foi removido da VPS na mesma data (`.hermes.md`).

**O que ainda está em aberto:**

- `reference/flutter-apps/` (≈2.300 arquivos) continua no repositório. Vale a
  pena manter? A tag já guarda tudo isso.
- Documentos que descrevem código inexistente: `INVENTARIO_LEGADO_STACKFOOD.md`
  e `JAVA_MIGRATION_PLAN.md`. Arquivar, atualizar ou marcar como histórico?
- A licença do pacote comercial StackFood continua **a confirmar**
  (`reference/README.md`) — isso é uma pendência jurídica, não técnica.

**Por quê:** um repositório que descreve o que não existe mais confunde quem
chega depois — inclusive você daqui a seis meses.

**Quando:** quando incomodar. Não é urgente e não bloqueia nada.

**Como:** decidir entre (a) manter `reference/` e marcar os docs como
históricos, (b) mover tudo para um repositório `foodie-legacy`, ou (c) apagar
`reference/` confiando só na tag.

---

## Ideias soltas

*(Adicione aqui sem cerimônia. Uma linha basta.)*

- Integração com WhatsApp Business para notificações e suporte
- App próprio do cliente (Flutter ou React Native) — hoje é web
- App próprio do restaurante e entregador
- Integração com maquininha de cartão física (Stone, Cielo, PagSeguro)
- Programa de fidelidade com pontos resgatáveis em produtos, não só desconto
- Área pública de cupons e promoções (vitrine de ofertas do dia)
- Recomendação por IA ("quem pediu isso também gostou")
- Painel de gestão de múltiplas unidades para lojistas grandes
- Integração com iFood / Rappi / 99Food (canal adicional, não concorrente)
- Assinatura premium para o cliente final (frete grátis mensal, etc.)
- Marketplace de fornecedores entre lojistas da plataforma
- Impressão térmica direta na cozinha do restaurante
- Integração com sistemas de PDV existentes (Linx, Saipos, etc.)
- Relatório fiscal / integração com contabilidade
- **Deploy atômico com rollback:** hoje o deploy é pacote por cima do código
  (sem atomicidade, sem histórico). Um script versionado que baixa o release do
  commit, faz backup, troca a pasta e reverte em um comando — com registro de
  qual commit está no ar — resolveria a maior fragilidade operacional atual.
