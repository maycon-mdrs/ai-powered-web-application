package br.imd.ufrn.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@ConfigurationProperties(prefix = "gateway.rate-limit")
@RefreshScope
public class GatewayRateLimitProperties {

    private int perSecond = 5;

    public int getPerSecond() {
        return perSecond;
    }

    public void setPerSecond(int perSecond) {
        this.perSecond = perSecond;
    }
}
