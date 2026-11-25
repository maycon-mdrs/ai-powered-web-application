package br.imd.ufrn.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langsmith")
public class LangSmithProperties {

    /** Quando true e api-key presente, envia runs ao LangSmith. */
    private boolean tracing = false;

    private String apiKey = "";

    private String endpoint = "https://api.smith.langchain.com";

    private String project = "tp3-assistente-livros";

    public boolean isTracing() {
        return tracing;
    }

    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public boolean isEnabled() {
        return tracing && apiKey != null && !apiKey.isBlank();
    }
}
