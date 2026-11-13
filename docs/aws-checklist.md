# Deploy AWS Academy (checklist Fase H)

1. Uma EC2 com Java 21, Maven (ou jars pré-build), Node 18+ (MCP), Docker (obs opcional)
2. Abrir SG: 8080, 8761, 3000, 9411 (restrito ao seu IP)
3. Copiar jars + `config-repo` + scripts
4. `chmod +x scripts/start-local.sh` e subir stack
5. Criar Lambda Java com handler `br.imd.ufrn.lambda.PlanoLeituraHandler` (artifact `*-aws.jar`)
6. Function URL → `LAMBDA_BASE_URL`
7. Secrets: Vault na EC2 ou env na sessão
8. Ensaio: [07-ensaio-apresentacao.md](07-ensaio-apresentacao.md)
