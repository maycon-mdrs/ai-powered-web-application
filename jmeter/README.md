# JMeter

Planos de carga do assistente de livros.

## Alvo

`POST http://<gateway-host>:8080/api/assistant/chat`

Body de exemplo:

```json
{
  "conversationId": "load-1",
  "message": "Quero ficção científica curta",
  "gerarPlano": false
}
```

## Requisitos da banca

- Threads **> 5** e **abaixo do Knee Capacity**
- Summary Report com **0 erros** com sistema saudável
- Chaos Monkey ligado → erros sobem; desligado → erros descem
- Preencher tabela Knee / Usable em `resultados/` (criar na medição)

Ver [docs/05-observabilidade-e-carga.md](../docs/05-observabilidade-e-carga.md).
