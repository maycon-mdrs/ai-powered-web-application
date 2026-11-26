# 9. YAML linha a linha

Este capítulo não assume que você já sabe o que é Actuator, Eureka ou Resilience4j.  
Para cada arquivo: **o que o arquivo é**, **o que cada chave controla**, **como você vê o efeito**.

Há dois “mundos” de YAML:

| Mundo | Onde | Quando entra |
|-------|------|----------------|
| **Bootstrap** (dentro do jar) | `*/src/main/resources/application.yml` | Sempre, ao subir o serviço |
| **Config remoto** | `config-repo/*.yml` (via Config Server) | Depois do bootstrap; pode **sobrescrever** chaves iguais |

Na banca você mexe sobretudo no `config-repo/` + `POST /actuator/refresh`.  
Glossário rápido: [10-glossario.md](10-glossario.md).

---

## Antes de tudo: o que é Actuator?

**Actuator** = módulo do Spring Boot que abre URLs de **operação** (não de negócio) em cada serviço, sob o prefixo `/actuator/...`.

Exemplos neste projeto:

| URL | O que responde |
|-----|----------------|
| `GET /actuator/health` | O serviço está vivo? (UP/DOWN) e, se configurado, **detalhes** (disco, Eureka, CB…) |
| `POST /actuator/refresh` | “Relê a config do Config Server e atualiza beans `@RefreshScope`” |
| `GET /actuator/circuitbreakers` | Estado CLOSED/OPEN/HALF_OPEN do Resilience4j |
| `GET /actuator/prometheus` | Métricas no formato que o Prometheus raspa |
| `…/chaosmonkey/…` | Liga/desliga falhas artificiais (só no coordenador) |

Sem Actuator você só teria as APIs de negócio (`/api/assistant/chat`). Com ele, a banca vê saúde, CB, refresh e métricas **sem** abrir o banco ou o código.

A chave YAML `management:` é o namespace do Actuator (“management” = administração da app).

---

## 9.1 `config-repo/application.yml`

Arquivo **comum a todos** os serviços que falam com o Config Server. O Config junta isto + o YAML do nome do serviço (`ms-ia.yml`, etc.).

```yaml
management:
  endpoint:
    health:
      show-details: always
```

| Chave | Significado em português |
|-------|--------------------------|
| `management` | Configura o Actuator (URLs `/actuator/*`). |
| `endpoint.health` | Configura só o endpoint de saúde (`/actuator/health`). |
| `show-details: always` | Por padrão o health devolve só `{ "status": "UP" }` (esconde detalhes por segurança). Com `always`, qualquer um que chamar o health vê **componentes** (ex.: disco, circuit breakers registrados). Na demo da banca isso ajuda: você abre o browser e mostra “está UP e o Eureka está ok”. |

**Como ver:** `curl http://localhost:8081/actuator/health` (ou a porta do serviço) — compare mentalmente com um health “cego” que só diz UP.

**O que este arquivo *não* faz:** não liga o Actuator sozinho (a dependência e o `exposure.include` ficam no bootstrap de cada serviço). Ele só diz: “quando o health existir, mostre detalhes”.

---

## 9.2 `config-repo/ms-ia.yml`

Só o serviço cujo `spring.application.name` é `ms-ia` recebe este arquivo.

```yaml
ai:
  provider: gemini
  stub: true
```

No Java existe a classe `AiProperties` com `@ConfigurationProperties(prefix = "ai")` e `@RefreshScope`.  
Tradução: as chaves `ai.*` deste YAML viram campos Java (`provider`, `stub`). Depois de um **refresh**, o Spring recria esse bean com os valores novos.

| Chave | O que controla | Se mudar para… |
|-------|----------------|----------------|
| `ai.provider` | Qual fábrica de modelo/embeddings o código prefere (`gemini` ou `openai`). Usado em `AiModelSelectionConfig` (chat + embeddings RAG). | `openai` → tenta beans OpenAI (precisa de key no Vault). |
| `ai.stub` | Atalho de demo/carga. `true` = `BookAssistantService` **não chama** Gemini/OpenAI; devolve `ai.stub-message`. `false` = chama a API de verdade (custa token e latência). | `false` + refresh/recreate → chat “inteligente”; JMeter costuma ficar em `true`. |
| `ai.stub-message` | String do mock; a API devolve **exatamente** este texto. | Na banca, deixa óbvio que o LLM está desligado. |

**Como ver o efeito:**

1. Com `stub: true` → resposta começa com `[stub/gemini]…`.
2. Push `stub: false` → `curl -X POST http://…:8082/actuator/refresh` (ou recreate do container) → próxima pergunta sem o prefixo stub.

`${VAR:default}` **não** aparece neste arquivo; aqui os valores são literais de propósito, para a banca editar no Git.

---

## 9.3 `config-repo/api-gateway.yml`

Só o `api-gateway` consome.

```yaml
gateway:
  rate-limit:
    per-second: 40
```

No Java: `GatewayRateLimitProperties` (`prefix = "gateway.rate-limit"`, `@RefreshScope`).  
O filtro `InMemoryRateLimitFilter` pergunta `getPerSecond()` a cada request.

| Chave | O que controla |
|-------|----------------|
| `gateway.rate-limit.per-second` | Quantos requests **por segundo**, **por IP do cliente**, o Gateway aceita. O 41º no mesmo segundo recebe **HTTP 429** e **não** chega ao coordenador. |

**Por que 40?** Com 5/s o JMeter gerava 429 cedo demais na demo; 40 deixa 5–8 usuários confortáveis e ainda mostra saturação em ~12 usuários (ver [docs/10](../10-resultados-pos-refatoracao-2026-11-26.md)).

**Como ver:** baixe para `2`, refresh em `:8080`, dispare vários curls rápidos → 429.

---

## 9.4 `config-repo/ms-coordenador.yml`

Config remota do orquestrador. Complementa (e pode sobrescrever) o YAML de dentro do jar.

### URLs e timeouts

```yaml
ms-ia:
  base-url: ${MS_IA_BASE_URL:http://MS-IA}

lambda:
  base-url: ${LAMBDA_BASE_URL:http://localhost:9000}

http:
  client:
    connect-timeout: 3s
    read-timeout: 45s
```

| Chave | Significado |
|-------|-------------|
| `${MS_IA_BASE_URL:http://MS-IA}` | Sintaxe Spring: “use a variável de ambiente `MS_IA_BASE_URL`; se não existir, use `http://MS-IA`”. |
| `http://MS-IA` | **Não** é um DNS da internet. É o **nome do serviço no Eureka**. O `RestClient` `@LoadBalanced` troca isso pelo IP:porta da instância registrada. |
| `lambda.base-url` | URL **absoluta** da Function URL (ou mock local). A Lambda **não** está no Eureka. |
| `connect-timeout: 3s` | Tempo máximo para abrir a conexão TCP. Se o host não responde, falha em ~3s (vira falha no CB). |
| `read-timeout: 45s` | Tempo máximo esperando o **corpo** da resposta. LLM pode demorar; 45s evita travar a thread para sempre. |

### Chaos Monkey (só a flag remota)

```yaml
chaos:
  monkey:
    enabled: ${CHAOS_MONKEY_ENABLED:false}
```

| Chave | Significado |
|-------|-------------|
| `chaos.monkey.enabled` | Se o “macaco do caos” está apto a injetar latência/exceções. Default `false`. Na demo você ainda precisa do profile `chaos-monkey`, da lib 4.x e do `POST …/chaosmonkey/enable` — ver item 9 do guia. |

### Resilience4j (números que o proxy lê)

O Java só diz `@CircuitBreaker(name = "msIa")`. **Os números** vêm daqui (ou do bootstrap se o Config estiver offline).

| Chave | Significado em português |
|-------|--------------------------|
| `circuitbreaker.instances.msIa` | Parâmetros do disjuntor das chamadas GraphQL ao MS-IA. |
| `slidingWindowSize: 10` | Olha as **últimas 10** chamadas para calcular a taxa de falha. |
| `failureRateThreshold: 50` | Se ≥ 50% dessas 10 falharam → abre o circuito (OPEN). |
| `waitDurationInOpenState: 5s` | Fica OPEN 5 segundos; depois tenta HALF_OPEN. |
| `bulkhead…maxConcurrentCalls: 8` | No máximo **8** chamadas ao MS-IA **ao mesmo tempo** nesta instância do coordenador. A 9ª é recusada na hora. |
| `ratelimiter…limitForPeriod: 15` | No máximo **15** chamadas por período. |
| `limitRefreshPeriod: 1s` | O período é 1 segundo → 15/s. |
| `timeoutDuration: 0` | Se não houver “crédito” agora, **não espera** na fila: falha imediata. |
| Instância `lambda` | Mesma ideia, para `gerarPlano` (limites menores: BH 4, RL 10/s). |

**Como ver CB:** `docker stop ms-ia` → vários chats → `GET :8081/actuator/circuitbreakers` → `msIa` OPEN.

**Refresh:** nos **dois** containers (`:8081` e `:8083`), senão uma réplica fica com número velho.

---

## 9.5 Bootstrap do Gateway (`api-gateway/.../application.yml`)

Arquivo **dentro da imagem**. Define o mínimo para o Gateway nascer mesmo sem Config.

| Bloco / linhas | O que é | Em português |
|----------------|---------|--------------|
| `server.port: 8080` | Porta HTTP | O processo escuta em 8080 (entrada da banca). |
| `spring.application.name: api-gateway` | Nome lógico | Nome no Eureka **e** nome do arquivo que o Config procura (`api-gateway.yml`). |
| `spring.config.import: …configserver:…` | Ligação ao Config | Ao subir, pergunta ao Config Server as configs remotas. `optional:` = se o Config estiver fora, sobe mesmo assim com o YAML local. |
| `discovery.locator.enabled: true` | Rotas **só dinâmicas** | Cria `/ms-coordenador/**` a partir do Eureka; é o único mecanismo de rota de negócio neste projeto. |
| `lower-case-service-id: true` | Path em minúsculas | Service id `MS-COORDENADOR` vira prefixo `/ms-coordenador`. |
| `eureka.client.service-url.defaultZone` | Onde está o catálogo | Lista dos peers Eureka (8761 e 8762). |
| `eureka.instance.*` | Como este Gateway se anuncia | IP/hostname que os outros usam para achá-lo. |
| `gateway.rate-limit.per-second: 40` | Default local | Mesmo significado do config-repo; o remoto pode sobrescrever. |
| `management.endpoints.web.exposure.include` | Quais Actuators existem na web | Lista explícita: `health`, `refresh`, `prometheus`, `gateway`, … Sem estar aqui, a URL dá 404. |
| `management.metrics…` | Histogramas HTTP | Alimenta gráficos de latência no Grafana. |
| `management.tracing… zipkin` | Tracing distribuído | Manda spans para o Zipkin (`ZIPKIN_ENDPOINT`). `probability: 1.0` = amostra 100% (bom para demo, caro em produção). |

---

## 9.6 Bootstrap do coordenador (`ms-coordenador/.../application.yml`)

| Bloco | Em português |
|-------|----------------|
| `server.port` / `SERVER_PORT` | 8081 por padrão; a réplica `b` no Compose usa 8083 via env. |
| `spring.application.name: ms-coordenador` | Id Eureka = `MS-COORDENADOR` (maiúsculas no registro). |
| `lease-renewal-interval-in-seconds: 5` | A cada ~5s o serviço grita “estou vivo” para o Eureka. |
| `lease-expiration-duration-in-seconds: 15` | Se ficar ~15s sem gritar, o Eureka **remove** da lista. Por isso o kill da réplica aparece rápido na demo. |
| `ms-ia` / `lambda` / `http.client` | Mesmos significados da §9.4 (defaults se Config falhar). |
| `management.endpoints…include: …,circuitbreakers,…,chaosmonkey` | Expõe na web os endpoints que a banca abre no browser/curl. |
| `management.endpoint.chaosmonkey.access: unrestricted` | No Boot 4, só “incluir” na lista **não basta**: sem `access: unrestricted` o `/actuator/chaosmonkey` responde **404**. |
| `resilience4j.*` | Cópia local completa dos números (inclui `retry`, que no config-repo remoto pode não estar). Garante CB/retry mesmo offline do Config. |
| `chaos.monkey.assaults` | Quando o caos está enable: a cada N chamadas (level), injeta latência 1–3s e/ou exceções em `@RestController` / `@Service`. |

---

## 9.7 Bootstrap do MS-IA (`ms-ia/.../application.yml`)

| Bloco | Em português |
|-------|----------------|
| `server.port: 8082` | Porta do GraphQL / GraphiQL. |
| `spring.graphql.graphiql.enabled: true` | Liga a UI em `/graphiql` (IDE no browser para testar mutations). |
| `spring.ai.openai` / `spring.ai.google.genai` | Auto-config Spring AI: keys (env/Vault) + nome do modelo de chat. |
| `spring.ai.mcp.client` | Cliente MCP. `enabled` via `MCP_ENABLED`. `stdio.connections.open-library` = comando que sobe o processo `mcp-open-library` (`npx`…). |
| `spring.cloud.vault.enabled: false` | No perfil **default**, Vault desligado (dev stub sem cofre). |
| `ai.provider` / `ai.stub` | Defaults locais; o `config-repo/ms-ia.yml` costuma mandar na prática. |
| Bloco `---` + `on-profile: vault` | Segundo documento YAML no mesmo arquivo: só ativa com profile `vault`. Importa `vault://`, `fail-fast: true` (se o Vault estiver fora, o MS-IA **não sobe**), lê KV `secret/ms-ia`. |

---

## 9.8 Config Server (`config-server/.../application.yml`)

| Chave | Em português |
|-------|----------------|
| `CONFIG_PROFILE` / `spring.profiles.active` | `native` = lê pasta no disco (`config-repo`). `git` = clona o GitHub e lê de lá (EC2). |
| `native.search-locations` | Caminho filesystem da pasta de YAMLs. |
| `git.uri` | URL do repositório (este projeto). |
| `git.search-paths: config-repo` | Só a pasta `config-repo/` importa (não o resto do monorepo). |
| `git.force-pull` / `clone-on-start` | Garante cópia atualizada ao subir / ao buscar. |

Os outros serviços **não** leem o `config-repo` direto: eles pedem `http://config-server:8888/<nome>/default` e o Config Server devolve o YAML mesclado.

---

## 9.9 Eureka server

| Ideia | Em português |
|-------|----------------|
| Dois processos (`peer1` / `peer2`) | Cluster: cada um conhece o outro; se um peer cai, o catálogo ainda existe no outro. |
| Apps listam **os dois** em `defaultZone` | Cliente tenta um peer; tem fallback no outro. |
| `enable-self-preservation: false` | Desliga o modo em que o Eureka “segura” instâncias mortas sob suspeita de rede ruim. Na demo queremos remoção **rápida** após kill. |
| Eviction ~5s | Intervalo em que o servidor varre leases expirados. |

---

## Mini-roteiro “estou perdido nesta chave”

1. A chave começa com `management.`? → Actuator (este capítulo, topo).  
2. `eureka.`? → registro/descoberta de serviços.  
3. `resilience4j.`? → números do proxy em torno de `askIa` / `gerarPlano` ([cap. 6](06-resiliencia.md)).  
4. `ai.`? → stub/provider do MS-IA.  
5. `gateway.rate-limit.`? → 429 no filtro do Gateway.  
6. Ainda vago? → [glossário](10-glossario.md) ou [ATALHOS](ATALHOS.md) (arquivo Java que *lê* essa chave).
