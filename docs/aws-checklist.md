# Deploy AWS (checklist) — conta pessoal + Compose

1. EC2 Amazon Linux 2023 `t3.large` + **Elastic IP** + SG (22, 8080, 8761, 3000, 9411, …)
2. Na VM: Docker + Compose + `git clone`
3. No PC: IAM role `tp3-plano-leitura-role` + Lambda `plano-leitura`
4. Handler: `br.imd.ufrn.lambda.PlanoLeituraUrlHandler::handleRequest` (`*-aws.jar`)
5. Function URL → `LAMBDA_BASE_URL` no `.env` da EC2
6. `./scripts/docker-up.sh none` (ou `local-lambda` só no smoke sem AWS)
7. Smoke: `POST /api/assistant/chat` + `gerarPlano=true`
8. Ensaio: [07-ensaio-apresentacao.md](07-ensaio-apresentacao.md)
9. Guia completo: [ambiente-aws.md](ambiente-aws.md)
