# Assistente de livros (TP3 — AI-Powered Web Application)

Aplicação em microserviços Spring: conversa sobre livros com **Chat Memory**, **RAG de gêneros** e **MCP Open Library**, mais **Lambda** de plano de leitura.

## Comece pela documentação

Leia nesta ordem:

1. [docs/00-visao-geral.md](docs/00-visao-geral.md)
2. [docs/01-plataforma.md](docs/01-plataforma.md)
3. [docs/02-ms-coordenador.md](docs/02-ms-coordenador.md)
4. [docs/03-ms-ia.md](docs/03-ms-ia.md)
5. [docs/04-lambda.md](docs/04-lambda.md)
6. [docs/05-observabilidade-e-carga.md](docs/05-observabilidade-e-carga.md)
7. [docs/06-deploy-aws.md](docs/06-deploy-aws.md)

Corpus RAG: [docs/corpus/](docs/corpus/).

## Módulos

| Módulo | Papel |
|--------|--------|
| `eureka-server` | Service discovery (cluster 8761/8762) |
| `config-server` | Config central (`config-repo`) |
| `api-gateway` | Entrada HTTP :8080 |
| `ms-coordenador` | Orquestra GraphQL + Lambda + resiliência |
| `ms-ia` | Spring AI / GraphQL |
| `ms-lambda` | Função plano de leitura |

## Build

```bash
mvn -q -DskipTests package
```

Java 21. Versões alinhadas aos materiais do curso (Boot 4.1.1 / Cloud 2025.1.3).

## Estado atual

Skeleton + documentação dos escopos. Próximos passos: resiliência completa, Spring AI (Memory/RAG/MCP/fábrica LLM), Vault, observabilidade, JMeter e deploy AWS Academy.
