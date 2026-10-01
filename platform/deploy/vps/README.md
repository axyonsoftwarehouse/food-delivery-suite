# Provisionamento da VPS

`bootstrap-production.sh` é a cópia **idêntica** de `/root/bootstrap-production.sh` da VPS
(`production-01`, `2.29.42.104`), versionada em 01/10/2026 para não depender só do servidor.

| Item | Valor |
| --- | --- |
| Origem | `/root/bootstrap-production.sh` (root, `-rwx------`, 15/09/2026 01:11) |
| md5 | `4bf5a197e48bb0c7cb237e0f10bc14f8` (3175 bytes) — igual na VPS e aqui |
| Segredos | nenhum: a senha do Postgres é gerada na execução (`openssl rand`) e só a chave pública do root é copiada |

Conferir se a VPS ainda tem a mesma versão:

```bash
ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 'sudo md5sum /root/bootstrap-production.sh'
```

## O que ele faz

1. Atualiza o sistema e instala `ufw`, `fail2ban`, `unattended-upgrades`, Docker e Compose.
2. Cria o usuário `deploy` (grupos `sudo` e `docker`) com a chave pública do root.
3. Endurece o SSH (`PermitRootLogin no`, `PasswordAuthentication no`, `AllowUsers deploy`).
4. Firewall: nega entrada por padrão; libera só 22, 80 e 443.
5. `fail2ban` no SSH (5 tentativas em 10 min → 1 h de bloqueio).
6. Atualizações de segurança automáticas, sem reboot automático.
7. **Cria o stack `/opt/production`** (Postgres + pgbouncer + Redis em rede interna).

## Antes de reutilizar

- **Não rode como está.** A seção 7 recria o scaffold `production`, que nunca foi usado e foi
  **removido em 30/09/2026** (`docs/RUNBOOK_VPS.md` §8). Para um servidor novo, apague essa seção.
- A última linha é um `EOF` solto (resto de como o arquivo foi gerado). Com `set -e`, o bash tenta
  executá-lo como comando e o script termina com erro 127 — depois de já ter feito todo o resto.
- O script pede `edoburu/pgbouncer:1.24.1-p0`, mas a imagem removida da VPS em 30/09 era
  `v1.25.2-p0` — `(a confirmar)` se o compose foi editado à mão depois do provisionamento.
- O `fail2ban` comenta que o tráfego da aplicação seria limitado no Cloudflare; hoje o site é
  servido pelo Caddy direto (`RUNBOOK_VPS.md` §9) — `(a confirmar)` se há Cloudflare na frente.
