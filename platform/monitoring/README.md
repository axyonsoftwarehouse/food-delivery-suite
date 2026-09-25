# Monitoramento e alertas da plataforma Foodie

Sobe Prometheus + Alertmanager + blackbox-exporter **na rede interna** `foodie-staging_default`, sem publicar portas. O Prometheus raspa as métricas da API (`/actuator/prometheus`) e faz sondagens HTTP/TCP pelo blackbox; o Alertmanager entrega no **Telegram**.

## Configurar

1. Copie `.env.example` para `.env` (não versionado) e preencha:
   - `TELEGRAM_BOT_TOKEN`: token do bot.
   - `TELEGRAM_CHAT_ID`: id numérico do chat que recebe os alertas.
2. Renderize o config do Alertmanager com os valores e suba a pilha a partir de `platform/monitoring`:

   ```
   ./render.sh      # gera alertmanager.rendered.yml (fora do Git; 644 para o contêiner ler, .env fica 600)
   docker compose up -d
   ```

O `render.sh` lê `monitoring/.env` e substitui os placeholders de `alertmanager.yml` no arquivo renderizado; nenhum segredo fica versionado. Reexecute o `render.sh` sempre que alterar o `alertmanager.yml` ou o `.env`.

## O que é monitorado e alertado

| Alerta | Condição | Gravidade |
| --- | --- | --- |
| `FoodieApiDown` | `up{job="foodie-api"} == 0` por 1 min | crítica |
| `FoodieReadinessDown` | `/ready` ≠ 200 por 1 min | crítica |
| `FoodieWebDown` | site `web:3001` ≠ 200 por 2 min | crítica |
| `FoodieDatabaseDown` | TCP em `db:3306` falhando por 1 min | crítica |
| `FoodieHighErrorRate` | mais de 2% de 5xx em 5 min | aviso |
| `FoodieHighLatencyP95` | p95 > 800 ms em 5 min | aviso |
| `FoodieDatabasePoolSaturated` | pool Hikari > 90% | aviso |

"Contêiner unhealthy" é coberto pelas sondagens de disponibilidade (`api`, `web`, `db`): se o serviço parar de responder, o alerta dispara mesmo sem o Docker reportar `unhealthy`.

## Verificar

- Alvos do Prometheus: `docker compose exec prometheus wget -qO- http://127.0.0.1:9090/api/v1/targets`.
- Testar o Telegram enviando um alerta de exemplo:

  ```
  docker compose exec alertmanager amtool alert add FoodieTeste severity=info \
    summary='Teste de alerta Foodie' description='Telegram funcionando.' \
    --alertmanager.url=http://127.0.0.1:9093
  ```

- As métricas de p95 dependem de histograma habilitado na API (`management.metrics.distribution.percentiles-histogram.http.server.requests=true`).
