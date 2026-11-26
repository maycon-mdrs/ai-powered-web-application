# Guia do código · Assistente de Livros (TP3)

Este guia explica o código do **assistente de livros** para quem sabe Java básico e ainda não conhece Spring Boot nem Spring Cloud. Ele tem duas partes:

- **Parte 1, estudo** (capítulos 1 a 7): o mínimo de Spring, o mapa do sistema, uma requisição seguida classe por classe, os 14 itens do enunciado com o código comentado, a resiliência em detalhe e os 12 fatores / AWS.
- **Parte 2, consulta** (capítulos 8 a 11): uma ficha por classe, os YAML linha a linha, um glossário e o apêndice de decisões.

**Por que assim?** Os quadros explicam o porquê de cada escolha: a decisão, o motivo, a alternativa descartada e onde está no código. Cada um leva os IDs de [docs/DECISOES.md](../DECISOES.md); o [capítulo 11](11-decisoes-indice.md) lista o mapa decisão → capítulo.

Os trechos citam o **código atual** com caminho curto e linhas. Exemplo: `coordenador/service/AssistantOrchestrator.java:25-37` quer dizer `ms-coordenador/src/main/java/br/imd/ufrn/coordenador/service/AssistantOrchestrator.java`.

Para mostrar código ao professor, abra o repo no VS Code e use [ATALHOS.md](ATALHOS.md): para cada item do enunciado, “abrir o arquivo X na linha Y”. No VS Code, `Ctrl+P` e `AssistantOrchestrator.java:29` abrem direto na linha.

Este guia **não substitui** [docs/00](../00-visao-geral.md)–[10](../10-resultados-pos-refatoracao-2026-11-26.md) nem o [roteiro da banca](../09-roteiro-apresentacao-banca.md). É material de **estudo do código**.

---

## Parte 1 — estudo

1. [O mínimo de Spring para ler o código](01-spring-minimo.md)
2. [Mapa do sistema](02-mapa-sistema.md)
3. [Uma requisição pelo código](03-request-pelo-codigo.md)
4. [Itens 1 a 7: plataforma e integração](04-itens-1-a-7.md)
5. [Itens 8 a 14: Vault, caos, resiliência e Spring AI](05-itens-8-a-14.md)
6. [Resiliência em detalhe](06-resiliencia.md)
7. [12 fatores, AWS e capacidade](07-doze-fatores-e-aws.md)

## Parte 2 — consulta

8. [Fichas por classe](08-fichas-classes.md)
9. [YAML linha a linha](09-yaml-linha-a-linha.md)
10. [Glossário](10-glossario.md)
11. [Decisões e onde aparecem](11-decisoes-indice.md)

## Atalhos da banca

- [ATALHOS.md](ATALHOS.md) — arquivo + linha por item 1–14
