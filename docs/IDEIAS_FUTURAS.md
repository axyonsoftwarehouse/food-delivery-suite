# Ideias futuras — Foodie

Documento de memória. Última atualização: 29/09/2026.
**Não é backlog. Não é compromisso.** É onde ideias ficam guardadas para não
se perderem. Quando uma ideia amadurecer, vira épico em
`PLANO_EPICOS.md` com número `E##`.

## Como usar

- **Ideia nova?** Adicione na seção "Ideias soltas", sem estrutura.
- **Ideia amadureceu?** Promova para uma seção com "O que é / Por quê / Quando".
- **Ideia morreu?** Marque como `❌ Descartada` com data e motivo. Não apague —
  o histórico evita que a mesma ideia seja discutida três vezes.

> **Nota sobre numeração.** O `PLANO_EPICOS.md` vai até **E48** (o
> "modo suporte" do admin, registrado em 01/10/2026). Números seguintes (E49 em
> diante) precisam ser registrados lá antes de serem tratados como épico.

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

**Status: resolvido em 01/10/2026.**

**O que era:** decidir o que fazer com o que restou do pacote legado (v9).

**Decisão: remover tudo.** Não há licença do pacote comercial — a chave não foi
encontrada e não será mais procurada — e a plataforma própria não depende dele.

**O que foi feito em 01/10/2026:**

- a pasta `reference/flutter-apps/` (≈2.350 arquivos, os três apps do pacote)
  saiu da árvore;
- a tag `legacy-v9` foi apagada, **local e no remoto** — era ela o
  atalho para recuperar o pacote inteiro (`admin-panel` 115 MB, `web`, `app-*`,
  `payment-gateway`);
- os documentos que descreviam o código do pacote saíram:
  `INVENTARIO_LEGADO.md`, `INVENTARIO_LACUNAS_LEGADO.md`,
  `JAVA_MIGRATION_PLAN.md`, `REFERENCIA_FUNCIONAL.md`, `AUDITORIA_IMPEDIMENTOS.md`,
  `REFERENCIA_INSPIRACOES.md`, `PLANO_EVOLUCAO_INSPIRACOES.md`,
  o documento de referência do kit visual de terceiros;
- o comentário do `tokens.css` que citava o kit visual de terceiros saiu (a
  paleta do produto continua igual).

O código do legado **continua no histórico do Git** (commits anteriores a
`04e5686`). Remover de verdade exigiria reescrever a história ou começar um
repositório novo — decisão separada, não tomada. A auditoria completa, com os
números, está em `docs/AUDITORIA_LEGADO_2026-10-01.md`.

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
