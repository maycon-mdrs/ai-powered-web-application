# Postman — TP3

## Importar

1. Abra o Postman → **Import**
2. Arraste a pasta `postman/` (ou selecione os 3 JSON):
   - `TP3-Assistente-Livros.postman_collection.json`
   - `TP3-EC2.postman_environment.json`
   - `TP3-Local.postman_environment.json`
3. No canto superior direito, selecione **TP3 — EC2** (ou Local)

## Environments

| Environment | `baseHost` |
|-------------|------------|
| TP3 — EC2 | `77.114.59.85` (ajuste se o EIP mudar) |
| TP3 — Local | `127.0.0.1` |

Variável útil: `conversationId` — use o mesmo valor nos turns de Memory.

## Pastas da collection

| Pasta | Uso |
|-------|-----|
| 0. Health / Eureka | Smoke + Config Server |
| 1. Chat | Gateway + Memory (2 turns) |
| 2. Plano / Lambda | Function URL via MS1 |
| 3. GraphQL | Direto no MS-IA (`/graphql`) |
| 4. Config refresh | Após push no `config-repo` |
| 5. Resiliência | CB, rate client, bulkhead, Chaos |
| 6. Rate limit 429 | Baseline; carga pesada → JMeter |

## Ordem sugerida na demo

1. Health + Eureka apps  
2. Chat turn 1 → turn 2  
3. Plano  
4. GraphQL  
5. Refresh (se alterou Git)  
6. Circuit breakers / Chaos  
