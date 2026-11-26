# 8. Fichas por classe

Formato fixo: **papel** · **métodos** · **quem a usa** · **o que ela usa** · **código comentado**.

Caminhos curtos: `gateway/…`, `coordenador/…`, `ia/…`, `lambda/…`.

---

## ApiGatewayApplication

`gateway/ApiGatewayApplication.java` · `@SpringBootApplication`  
**Papel.** Sobe o Gateway (WebFlux).  
**Quem a usa.** processo JVM.  
**Código comentado:** cap. 1, 2.

---

## InMemoryRateLimitFilter

`gateway/filter/InMemoryRateLimitFilter.java` · `@Component` · `GlobalFilter`, `Ordered`  
**Papel.** Rate limit por IP, janela 1s; 429 se estourar.  
**Métodos.** `filter`, `clientKey`, `getOrder` (−100).  
**Quem a usa.** cadeia de filtros do Gateway.  
**O que ela usa.** `GatewayRateLimitProperties`.  
**Código comentado:** cap. 3 parada 1; cap. 6.4.

---

## GatewayRateLimitProperties

`gateway/config/GatewayRateLimitProperties.java` · `@ConfigurationProperties(prefix="gateway.rate-limit")` · `@RefreshScope`  
**Papel.** Segura `perSecond` recarregável.  
**Código comentado:** itens 3–4, 12.

---

## AssistantController

`coordenador/web/AssistantController.java` · `@RestController` `/api/assistant`  
**Papel.** HTTP chat e plano.  
**Métodos.** `chat`, `plano`.  
**O que ela usa.** `AssistantOrchestrator`.  
**Código comentado:** cap. 3 paradas 3 e plano.

---

## AssistantOrchestrator

`coordenador/service/AssistantOrchestrator.java` · `@Service`  
**Papel.** Orquestra MS-IA e Lambda com Resilience4j.  
**Métodos.** `askIa` (+ `askFallback`), `gerarPlano` (+ `planoFallback`).  
**O que ela usa.** `MsIaGraphQlClient`, `PlanoLeituraClient`.  
**Código comentado:** cap. 3 parada 4; cap. 6.

---

## MsIaGraphQlClient

`coordenador/client/MsIaGraphQlClient.java` · `@Component`  
**Papel.** POST GraphQL load-balanced para MS-IA.  
**Métodos.** `askBookAssistant`.  
**O que ela usa.** `@LoadBalanced RestClient.Builder`, `ms-ia.base-url`.  
**Código comentado:** cap. 3 parada 5; item 5.

---

## PlanoLeituraClient

`coordenador/client/PlanoLeituraClient.java` · interface HTTP  
**Papel.** Contrato `@PostExchange("/plano")`.  
**Quem a cria.** `HttpClientsConfig.planoLeituraClient`.  
**Código comentado:** item 6–7; cap. 3.2.

---

## HttpClientsConfig

`coordenador/config/HttpClientsConfig.java` · `@Configuration`  
**Papel.** Beans `RestClient.Builder` (plain + `@LoadBalanced`) e proxy `PlanoLeituraClient`.  
**Métodos bean.** `restClientBuilder`, `loadBalancedRestClientBuilder`, `planoLeituraClient`.  
**Código comentado:** cap. 1.2; D15.

---

## ChatRequest / ChatResponse

`coordenador/dto/ChatRequest.java`, `ChatResponse.java`  
**Papel.** Contratos JSON do MS1. `ChatResponse` = `conversationId`, `response`, `source`; nested `PlanoLeituraDto`.  
**Código comentado:** D13; cap. 3.

---

## BookAssistantController

`ia/graphql/BookAssistantController.java` · `@Controller` GraphQL  
**Papel.** `health` query; mutation `askBookAssistant`.  
**O que ela usa.** `BookAssistantService`.  
**Código comentado:** cap. 3 parada 6; item 5.

---

## BookAssistantService

`ia/service/BookAssistantService.java` · `@Service`  
**Papel.** Stub ou ChatClient + memory; carrega system prompt.  
**Métodos.** `ask`, `loadSystemPrompt`, `callWithTransientRetry`, `isTransientLlmFailure`.  
**O que ela usa.** `AiProperties`, `ChatMemory`, `ChatClient`.  
**Código comentado:** cap. 3 parada 7; item 13.

---

## DocumentRagService

`ia/rag/DocumentRagService.java` · `@Service`  
**Papel.** Carrega `documents/*.md`; embeddings → `SimpleVectorStore` ou lexical.  
**Métodos.** `load`, `retrieve` (seleção de embedding via `AiModelSelectionConfig`).  
**Código comentado:** cap. 3 parada 8; D12, D21.

---

## RagTools

`ia/rag/RagTools.java` · `@Component`  
**Papel.** Expõe `@Tool searchGenreDocuments`.  
**O que ela usa.** `DocumentRagService`.  
**Código comentado:** item 13.

---

## AiConfig

`ia/config/AiConfig.java` · `@Configuration`  
**Papel.** Beans `ChatMemory`, `ChatClient` (+ tools RAG/MCP).  
**Código comentado:** cap. 3 parada 8.

---

## AiProperties

`ia/config/AiProperties.java` · `@ConfigurationProperties(prefix="ai")` · `@RefreshScope`  
**Papel.** `provider`, `stub`, `stubMessage`.  
**Código comentado:** itens 4 e 13.

---

## PlanoLeituraService

`lambda/PlanoLeituraService.java` · `@Service`  
**Papel.** Calcula páginas/dia e checklist.  
**Métodos.** `gerar`.  
**Código comentado:** item 7; cap. 3.2.

---

## PlanoLeituraUrlHandler / PlanoLeituraHandler

`lambda/PlanoLeituraUrlHandler.java`, `PlanoLeituraHandler.java`  
**Papel.** Adaptadores AWS (Function URL / eventos) para o serviço.  
**Código comentado:** item 7; docs/04-lambda.

---

## MsIaApplication / MsCoordenadorApplication / …

`*Application.java` em cada módulo  
**Papel.** Entry Boot.  
**Código comentado:** cap. 1.3.
