# Critérios de recomendação (assistente de livros)

Regras gerais que o RAG deve reforçar em qualquer gênero.

## Como usar memória + RAG + MCP
1. **Memória:** respeitar gênero, limite de páginas, autores/livros já citados na conversa.
2. **RAG:** usar o arquivo do gênero pedido (e este arquivo) para explicar o “porquê” da recomendação.
3. **MCP Open Library:** buscar títulos reais; não inventar ISBN/autor quando a tool retornar dados.

## Preferências comuns
- **Curto:** priorizar obras com menos páginas quando o metadado existir; senão, avisar incerteza.
- **Iniciante no gênero:** clássicos acessíveis ou standalones.
- **Já li X:** pedir obras relacionadas por autor/assunto via MCP, sem repetir X.
- **Série:** deixar claro se é volume 1 ou se exige leitura prévia.

## Formato da resposta ao usuário
- 2 a 5 sugestões, cada uma com: título, autor, por que combina, e um cuidado (tamanho/série/tom)
- Perguntar um refinador se faltar dado (páginas, tom, idioma)
- Se o usuário pedir plano de leitura, o **coordenador** chama a Lambda — o MS2 só recomenda o livro

## O que não fazer
- Inventar disponibilidade em livrarias locais
- Spoilar finais
- Ignorar o `conversationId` / histórico
