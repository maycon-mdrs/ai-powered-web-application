# 10. Glossário

| Termo | O que é neste projeto |
|-------|------------------------|
| **Actuator** | Endpoints de operação (`/actuator/health`, `/refresh`, `/circuitbreakers`, `/chaosmonkey`, `/prometheus`). |
| **Advisor** | Interceptador do `ChatClient` (ex.: memória com `MessageChatMemoryAdvisor`). |
| **AOP** | Programação orientada a aspectos: o proxy Resilience4j envolve o método anotado. |
| **Bean** | Objeto gerenciado pelo Spring (criado via anotação ou `@Bean`). |
| **Bulkhead** | Limite de chamadas **simultâneas** a um recurso (`maxConcurrentCalls`). |
| **Chat Memory** | Histórico de mensagens por `conversationId` (janela de 20 no MS-IA). |
| **Circuit Breaker** | Abre o circuito após taxa de falha; rejeita chamadas até half-open. |
| **Config Server** | Serviço que serve YAML versionado (native/Git) aos demais. |
| **Discovery / Eureka** | Catálogo de instâncias; `lb://NOME` resolve IP/porta. |
| **Embedding** | Vetor numérico de um texto; base da busca semântica no RAG. |
| **Fallback** | Resposta alternativa quando a chamada principal falha (`askFallback`, `planoFallback`). |
| **Function URL** | URL HTTPS pública da Lambda (plano de leitura). |
| **GraphQL** | API do MS-IA (`/graphql`); mutation `askBookAssistant`. |
| **GraphiQL** | IDE no browser (`/graphiql`) para testar GraphQL. |
| **HTTP Interface** | Interface Java com `@PostExchange` → cliente HTTP gerado. |
| **Load balancer (client)** | Spring Cloud LoadBalancer + Eureka no `RestClient` `@LoadBalanced`. |
| **MCP** | Model Context Protocol; aqui só **cliente** para Open Library. |
| **RAG** | Retrieval-Augmented Generation: documentos de gênero + tool `searchGenreDocuments`. |
| **Rate limiter** | Limite de taxa: **servidor** (Gateway) ou **cliente** (Resilience4j no MS1). |
| **RefreshScope** | Bean recriado após `/actuator/refresh` com config nova. |
| **Retry** | Repetir a chamada após falha transitória. |
| **SimpleVectorStore** | Vector store em memória do Spring AI (embeddings no boot). |
| **Stub** | Modo sem LLM (`ai.stub=true`): devolve exatamente `ai.stub-message` do YAML/Config. |
| **Tool** | Função que o modelo pode invocar (`@Tool` / MCP callbacks). |
| **Vault** | Cofre de segredos; KV `secret/ms-ia` para chaves LLM. |
| **Zipkin** | UI de traces distribuídos (`:9411`). |
