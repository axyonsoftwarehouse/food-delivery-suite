# AGENTS — Foodie

Instruções para agentes de IA que trabalham neste repositório.

Leia, antes de agir:

- `.hermes.md` — contexto e regras do projeto;
- `CONTRIBUTING.md` — fluxo de trabalho (PR, commits, deploy, segredos);
- `docs/ESTADO_ATUAL.md` — estado atual e próximo passo;
- `docs/RUNBOOK_VPS.md` — operação da VPS.

## Regras não negociáveis

- **Nunca** commitar nem dar push sem pedido explícito.
- **Nunca** abrir `.env*` nem arquivos de credenciais.
- A `main` é **protegida**: o trabalho entra por **PR** com CI verde
  (`Verificação`) e 1 aprovação — nunca push direto.
- O repositório é **público**: nada de segredos no diff.
- Fato não confirmado entra como `(a confirmar)` — não inventar para preencher
  lacuna.
- Prefira leitura dirigida; evite varrer o repositório inteiro.
