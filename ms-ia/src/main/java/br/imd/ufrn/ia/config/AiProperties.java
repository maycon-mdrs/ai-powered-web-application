package br.imd.ufrn.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@ConfigurationProperties(prefix = "ai")
@RefreshScope
public class AiProperties {

    /** Provedor ativo: {@code gemini} ou {@code openai}. */
    private String provider = "gemini";

    /** Quando {@code true}, não invoca o LLM (respostas determinísticas). */
    private boolean stub = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isStub() {
        return stub;
    }

    public void setStub(boolean stub) {
        this.stub = stub;
    }
}
