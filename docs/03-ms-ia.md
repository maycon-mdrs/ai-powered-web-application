# E3 — MS2 IA (`ms-ia`)

Em uma frase: este serviço é o **cérebro** — GraphQL + Spring AI com **Chat Memory**, **RAG de documentos**, **fábrica Gemini/OpenAI** e **MCP Open Library** (terceiro).

---

## 1. Responsabilidades

| Faz | Não faz |
|-----|---------|
| API GraphQL `askBookAssistant` | Orquestrar Lambda |
| Chat Memory por `conversationId` | Expor porta pública (só via Gateway→MS1) |
| RAG embeddings + tool `searchGenreDocuments` | MCP servidor próprio |
| Cliente MCP Open Library | Resilience4j completo (fica no MS1) |
| Trocar LLM via `ai.provider` | — |

---

## 2. Fluxo interno de uma pergunta

```mermaid
sequenceDiagram
  participant MS1 as ms-coordenador
  participant GQL as GraphQL_Controller
  participant Mem as ChatMemory
  participant Rag as DocumentRagService
  participant Tools as MCP_OpenLibrary
  participant LLM as ChatModel_Gemini_ou_OpenAI
  MS1->>GQL: askBookAssistant
  GQL->>Mem: carrega historico conversationId
  GQL->>LLM: prompt + memoria + tools
  LLM->>Rag: tool searchGenreDocuments(query)
  Note over Rag: embed query + SimpleVectorStore
  Rag-->>LLM: trechos de documents/
  LLM->>Tools: search_books / get_book_by_title
  Tools-->>LLM: livros reais
  LLM-->>GQL: response
  GQL->>Mem: grava turn
  GQL-->>MS1: response
```

---

## 3. GraphQL

```graphql
type Query {
  health: String
}

type Mutation {
  askBookAssistant(conversationId: String!, message: String!): BookAssistantReply!
}

type BookAssistantReply {
  conversationId: String!
  response: String!
}
```

---

## 4. Chat Memory

- Chave: `conversationId` (vindo do cliente/JMeter)
- Guarda turns user/assistant na sessão de demo
- Demo: 1ª mensagem define gênero; 2ª diz “como eu disse / mais curto” e a resposta usa o histórico

---

## 5. RAG (documentos + embeddings + tool)

Arquivos em `ms-ia/src/main/resources/documents/` (cópia em [`docs/documents/`](documents/)): `romance.md`, `ficcao-cientifica.md`, `suspense.md`, `fantasia.md`.

| Momento | O que acontece |
|---------|----------------|
| Boot (`ai.stub=false`) | Cada `.md` → embedding via `EmbeddingModel` → `SimpleVectorStore` (memória) |
| Consulta | LLM chama tool `searchGenreDocuments(query)` → embed da query → top-2 similarity |
| Stub / sem embedding | Fallback lexical por tokens (JMeter sem chave) |

Regras gerais ficam em `prompts/system-prompt.txt` (instrução para usar a tool). Frontmatter `tags:` é opcional e **não** entra no scoring.

---

## 6. MCP de terceiro — Open Library

- Pacote: [`mcp-open-library`](https://github.com/8enSmith/mcp-open-library)
- Subir: `npx -y mcp-open-library` (stdio) integrado ao Spring AI MCP client
- **Sem API key**
- Tools úteis na demo: `search_books`, `get_book_by_title`, `get_authors_by_name`

```mermaid
flowchart LR
  MS2[ms-ia] -->|"MCP client"| Proc[npx_mcp-open-library]
  Proc --> OL[OpenLibrary_API_publica]
```

**Fora de escopo:** servidor MCP próprio.

---

## 7. Fábrica Gemini / OpenAI

```mermaid
flowchart TD
  Cfg["ai.provider do Config Server"] --> Factory[ChatModelFactory]
  Factory -->|gemini| Gemini[ChatModel_Gemini]
  Factory -->|openai| OpenAI[ChatModel_OpenAI]
  Gemini --> Client[ChatClient]
  OpenAI --> Client
```

- Default: **gemini**
- Troca: alterar `ai.provider` no `config-repo` + refresh (ver [01-plataforma.md](01-plataforma.md))
- Chaves só no Vault
- LangSmith (opcional): `LangSmithTracingAdvisor` + `LANGSMITH_*` no Vault — Java não tem auto-trace por env como o Python

---

## 8. O que a banca pede ver

| Item | Demo |
|------|------|
| Spring AI + LLM | Pergunta real com resposta |
| Chat Memory | Duas perguntas na mesma `conversationId` |
| RAG | Tool `searchGenreDocuments` + `documents/` no SimpleVectorStore |
| MCP terceiro | Tool call Open Library na resposta |
| MCP próprio | **Não aplicável neste projeto** |
| GraphQL | Mutation no GraphiQL ou log do MS1 |

---

## 9. Perguntas de demo sugeridas

1. `conversationId=demo-1`: “Quero ficção científica curta.”
2. Mesma conversa: “Algo com menos de 300 páginas, no estilo do que eu pedi.”
3. “Monte recomendações de fantasia para quem gosta de magia e mundo secundário.”
