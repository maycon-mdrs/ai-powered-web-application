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

    /** Texto devolvido no modo stub — valor vem só do YAML/Config Server. */
    private String stubMessage;

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

    public String getStubMessage() {
        return stubMessage;
    }

    public void setStubMessage(String stubMessage) {
        this.stubMessage = stubMessage;
    }
}
