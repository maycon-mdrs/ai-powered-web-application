package br.imd.ufrn.chatdemo;

import java.util.ArrayList;
import java.util.List;

import io.javelit.core.Jt;
import io.javelit.core.Server;

public final class ChatDemoApp {

    private ChatDemoApp() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("JAVELIT_PORT", "8501"));
        Server.builder(ChatDemoApp::app, port).build().start();
    }

    @SuppressWarnings("unchecked")
    static void app() {
        String gateway = System.getenv().getOrDefault("GATEWAY_BASE_URL", "http://127.0.0.1:8080");
        GatewayChatClient client = new GatewayChatClient(gateway);

        Jt.title("Book Assistant").use();
        Jt.markdown("Demo **Javelit** → API Gateway → coordenador → MS-IA / Lambda.").use();

        String conversationId = Jt.textInput("conversationId")
                .value("demo-javelit-1")
                .help("Mesmo id = Chat Memory entre turnos")
                .use(Jt.SIDEBAR);

        boolean gerarPlano = Jt.checkbox("gerarPlano (Lambda)").use(Jt.SIDEBAR);

        String titulo = "Neuromancer";
        String autor = "William Gibson";
        int paginas = 271;
        int dias = 14;
        if (gerarPlano) {
            titulo = Jt.textInput("Título do livro").value(titulo).use(Jt.SIDEBAR);
            autor = Jt.textInput("Autor").value(autor).use(Jt.SIDEBAR);
            Number p = Jt.numberInput("Páginas", Integer.class).value(paginas).minValue(1).use(Jt.SIDEBAR);
            Number d = Jt.numberInput("Dias", Integer.class).value(dias).minValue(1).use(Jt.SIDEBAR);
            if (p != null) {
                paginas = p.intValue();
            }
            if (d != null) {
                dias = d.intValue();
            }
        }

        Jt.markdown("Gateway: `" + gateway + "`").use(Jt.SIDEBAR);

        if (Jt.button("Limpar histórico").use(Jt.SIDEBAR)) {
            Jt.sessionState().put("history", new ArrayList<String>());
        }

        Jt.sessionState().putIfAbsent("history", new ArrayList<String>());
        List<String> history = (List<String>) Jt.sessionState().get("history");
        if (history == null) {
            history = new ArrayList<>();
            Jt.sessionState().put("history", history);
        }

        String message = Jt.textArea("Mensagem")
                .value("Quero ficção científica curta")
                .height(100)
                .use();

        if (Jt.button("Enviar").use()) {
            if (message == null || message.isBlank()) {
                Jt.warning("Digite uma mensagem.").use();
            } else {
                history.add("**Você:** " + message.trim());
                try {
                    var result = client.chat(
                            conversationId == null || conversationId.isBlank() ? "demo-javelit-1" : conversationId.trim(),
                            message.trim(),
                            gerarPlano,
                            titulo,
                            autor,
                            paginas,
                            dias
                    );
                    if (result.ok()) {
                        history.add("**Assistente** (`" + result.source() + "`):\n\n" + result.reply());
                        if (result.plano() != null) {
                            history.add(result.plano());
                        }
                    } else {
                        history.add("**Erro HTTP " + result.status() + "**\n\n```\n"
                                + truncate(result.rawError(), 1500) + "\n```");
                    }
                } catch (Exception ex) {
                    history.add("**Falha:** " + ex.getMessage());
                }
                Jt.sessionState().put("history", history);
            }
        }

        Jt.markdown("### Conversa").use();
        if (history.isEmpty()) {
            Jt.info("Envie uma mensagem para começar.").use();
        } else {
            for (String entry : history) {
                Jt.markdown(entry).use();
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
