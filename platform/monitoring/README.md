# Monitoramento e alertas da plataforma Foodie

Sobe Prometheus + Alertmanager + blackbox-exporter **na rede interna** `foodie-staging_default`, sem publicar portas. O Prometheus raspa as mÃ©tricas da API (`/actuator/prometheus`) e faz sondagens HTTP/TCP pelo blackbox; o Alertmanager entrega no **Telegram**.

## Configurar

1. Copie `.env.example` para `.env` (nÃ£o versionado) e preencha:
   - `TELEGRAM_BOT_TOKEN`: token do bot.
   - `TELEGRAM_CHAT_ID`: id numÃ©rico do chat que recebe os alertas.
2. Renderize o config do Alertmanager com os valores e suba a pilha a partir de `platform/monitoring`:

   ```
   ./render.sh      # gera alertmanager.rendered.yml (fora do Git; 644 para o contÃªiner ler, .env fica 600)
   docker compose up -d
   ```

O `render.sh` lÃª `monitoring/.env` e substitui os placeholders de `alertmanager.yml` no arquivo renderizado; nenhum segredo fica versionado. Reexecute o `render.sh` sempre que alterar o `alertmanager.yml` ou o `.env`.

## O que Ã© monitorado e alertado

| Alerta | CondiÃ§Ã£o | Gravidade |
| --- | --- | --- |
| `FoodieApiDown` | `up{job="foodie-api"} == 0` por 1 min | crÃ­tica |
| `FoodieReadinessDown` | `/ready` â‰  200 por 1 min | crÃ­tica |
| `FoodieWebDown` | site `web:3001` â‰  200 por 2 min | crÃ­tica |
| `FoodieDatabaseDown` | TCP em `db:3306` falhando por 1 min | crÃ­tica |
| `FoodieHighErrorRate` | mais de 2% de 5xx em 5 min | aviso |
| `FoodieHighLatencyP95` | p95 > 800 ms em 5 min | aviso |
| `FoodieDatabasePoolSaturated` | pool Hikari > 90% | aviso |

"ContÃªiner unhealthy" Ã© coberto pelas sondagens de disponibilidade (`api`, `web`, `db`): se o serviÃ§o parar de responder, o alerta dispara mesmo sem o Docker reportar `unhealthy`.

## Verificar

- Alvos do Prometheus: `docker compose exec prometheus wget -qO- http://127.0.0.1:9090/api/v1/targets`.
- Testar o Telegram enviando um alerta de exemplo:

  ```
  docker compose exec alertmanager amtool alert add FoodieTeste severity=info \
    summary='Teste de alerta Foodie' description='Telegram funcionando.' \
    --alertmanager.url=http://127.0.0.1:9093
  ```

- As mÃ©tricas de p95 dependem de histograma habilitado na API (`management.metrics.distribution.percentiles-histogram.http.server.requests=true`).
