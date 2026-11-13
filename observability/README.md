# Observabilidade (aula)

```bash
docker compose -f observability/docker-compose.yml up -d
```

1. Prometheus: http://localhost:9090 — targets UP
2. Grafana: http://localhost:3000 (`admin`/`admin`)
   - Data source Prometheus → `http://prometheus:9090`
   - Import dashboard Spring Boot (grafana.com) + painéis de `resilience4j_circuitbreaker_*`
3. Zipkin: http://localhost:9411/zipkin

Apps devem expor `/actuator/prometheus` (já configurado nos módulos).
