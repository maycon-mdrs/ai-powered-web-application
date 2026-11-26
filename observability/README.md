# Observabilidade

Montada pelo **Compose raiz** (`docker-compose.yml`): Prometheus, Grafana e Zipkin.

| Serviço | URL |
|---------|-----|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (`admin`/`admin`) |
| Zipkin | http://localhost:9411/zipkin |

- Scrapes: `observability/prometheus.yml` (jobs `api-gateway`, `ms-coordenador`, `ms-ia`)
- Dashboard: `observability/grafana/dashboards/tp3-overview.json`
- Provisioning: `observability/grafana/provisioning/`

Apps expõem `/actuator/prometheus`. Não há Compose separado nesta pasta.
