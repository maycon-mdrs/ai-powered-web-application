# 1. O mínimo de Spring para ler o código

Este capítulo explica as peças do Spring que aparecem em todo o resto. Vale ler uma vez com calma; depois, cada capítulo só lembra o conceito quando ele reaparece.

---

## 1.1 Anotação

Uma **anotação** é uma etiqueta que começa com `@` e fica em cima de uma classe, de um método ou de um parâmetro. Sozinha, ela não faz nada: é só uma marca no código compilado. Quem lê as marcas é o Spring, quando a aplicação sobe, e ele decide o que fazer com cada uma.

Por isso um método com `@CircuitBreaker` ganha um circuit breaker sem nenhuma linha a mais: o Spring viu a etiqueta e montou o comportamento em volta (seção 1.4).

Exemplos neste projeto:

| Anotação | Onde | Efeito |
|----------|------|--------|
| `@SpringBootApplication` | `*Application.java` | Sobe o Boot, scan de componentes |
| `@RestController` | `AssistantController` | Métodos viram endpoints HTTP |
| `@Service` | `AssistantOrchestrator`, `BookAssistantService` | Bean de lógica de negócio |
| `@Component` | `InMemoryRateLimitFilter`, `RagTools` | Bean genérico |
| `@Configuration` + `@Bean` | `HttpClientsConfig`, `AiConfig` | Fábrica de beans |
| `@Tool` | `RagTools.searchGenreDocuments` | Ferramenta do ChatClient |
| `@MutationMapping` | `BookAssistantController` | Resolver GraphQL |

---

## 1.2 Bean e injeção de dependência

Um **bean** é um objeto que o Spring cria e guarda para você. Em vez de escrever `new AssistantOrchestrator(...)`, você marca a classe com `@Service` (ou `@Component`, `@RestController`, …) e o Spring cria **uma** instância ao subir.

**Injeção de dependência** é o Spring entregar a cada bean os outros beans de que ele precisa. No coordenador:

```java
// AssistantController.java — construtor
public AssistantController(AssistantOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
}
```

O Boot encontra um único bean `AssistantOrchestrator` e passa no construtor. Não há `new` na mão.

Beans também nascem de métodos `@Bean` dentro de `@Configuration`. Exemplo: o proxy HTTP da Lambda:

```java
// HttpClientsConfig.java:39-52
@Bean
PlanoLeituraClient planoLeituraClient(...) { ... }
```

---

## 1.3 Spring Boot e o `main`

Cada microserviço tem uma classe `*Application` com:

```java
@SpringBootApplication
public class MsCoordenadorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsCoordenadorApplication.class, args);
    }
}
```

`@SpringBootApplication` liga: auto-configuração, scan do pacote `br.imd.ufrn…` e o servidor embutido (Tomcat no MS1/MS2; Netty/WebFlux no Gateway).

Portas default neste projeto:

| Serviço | Porta |
|---------|-------|
| API Gateway | 8080 |
| MS-COORDENADOR | 8081 (réplica b: 8083) |
| MS-IA | 8082 |
| Config Server | 8888 |
| Eureka peers | 8761 / 8762 |

---

## 1.4 Proxy e AOP: por que `@CircuitBreaker` funciona

Quando você anota um método de um bean Spring com `@CircuitBreaker`, `@Retry`, `@Bulkhead` ou `@RateLimiter`, o Spring **não chama o método direto**. Ele entrega um **proxy**: um objeto que parece o bean, mas intercepta a chamada, aplica o padrão Resilience4j e só então (se permitido) chama o método real.

Consequência prática: anotações Resilience4j em `this.askIa(...)` **dentro da mesma classe** não passam pelo proxy — por isso o controller chama `orchestrator.askIa(...)`, e o orchestrator é outro bean.

No nosso código:

```java
// AssistantOrchestrator.java:25-31
@RateLimiter(name = "msIa")
@Bulkhead(name = "msIa")
@CircuitBreaker(name = "msIa", fallbackMethod = "askFallback")
@Retry(name = "msIa")
public String askIa(String conversationId, String message) {
    return msIaGraphQlClient.askBookAssistant(conversationId, message);
}
```

Os parâmetros (`slidingWindowSize`, `maxAttempts`, …) vêm do YAML (`resilience4j.*`), não do Java. Ver [capítulo 6](06-resiliencia.md).

---

## 1.5 De onde vem o `application.yml` de cada serviço

Na subida, o Spring monta o `Environment` juntando várias fontes. Neste projeto, a ordem mental é:

1. **`application.yml` do jar** (bootstrap local) — porta, nome, Eureka, defaults de resiliência.
2. **Config Server** (`spring.config.import: optional:configserver:…`) — lê `config-repo/<nome>.yml` (native) ou o Git (deploy).
3. **Vault** (só `ms-ia` com perfil `vault`) — `secret/ms-ia` vira propriedades (`GEMINI_API_KEY`, …).
4. **Variáveis de ambiente** do Compose/EC2 — `EUREKA_DEFAULT_ZONE`, `CONFIG_SERVER_URL`, `LAMBDA_BASE_URL`, `VAULT_TOKEN`, …

> **Por que assim?** · [D06](../DECISOES.md) · [D07](../DECISOES.md) · [D15](../DECISOES.md)  
> Config de comportamento (stub, rate, base-url) no Config/Git; segredos no Vault; endereços de infra no env. Mudar stub na banca = push + `/actuator/refresh`, sem rebuild.

Propriedades marcadas com `@RefreshScope` (ex.: `AiProperties`, `GatewayRateLimitProperties`) são recriadas no refresh — o bean novo lê o valor atualizado.

---

## 1.6 MVC (MS1/MS2) vs WebFlux (Gateway)

- **Spring MVC** (servlet): um request, uma thread (simplificando). Usado em `ms-coordenador` e `ms-ia` (`@RestController`, GraphQL controller).
- **Spring WebFlux** (Gateway): reativo (`Mono`/`Flux`). O filtro de rate limit devolve `Mono<Void>`:

```java
// InMemoryRateLimitFilter.java:28-43
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ...
    if (window.count.incrementAndGet() > perSecond) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
}
```

Você não precisa dominar WebFlux para a banca: basta saber que o Gateway é reativo e que o filtro global roda **antes** do proxy para o coordenador.

---

## 1.7 Actuator

O módulo **Actuator** expõe endpoints de operação sob `/actuator/*`:

| Endpoint | Uso na demo |
|----------|-------------|
| `/health` | Está vivo? |
| `/refresh` | Recarrega config do Config Server |
| `/circuitbreakers` | Estado CLOSED/OPEN/HALF_OPEN |
| `/prometheus` | Métricas para Grafana |
| `/chaosmonkey/*` | Liga/desliga assaults (MS1) |

Exposição controlada por `management.endpoints.web.exposure.include` em cada `application.yml`.

---

Próximo: [2. Mapa do sistema](02-mapa-sistema.md).
