package br.imd.ufrn.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * gemini | openai
     */
    private String provider = "gemini";

    /**
     * true = respostas determinísticas sem chamar LLM (JMeter / demos offline)
     */
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
