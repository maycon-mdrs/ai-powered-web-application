package br.imd.ufrn.coordenador.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import br.imd.ufrn.coordenador.client.PlanoLeituraClient;

@Configuration
public class HttpClientsConfig {

    /**
     * Builder padrão (sem LB) para Eureka/RestClient interno.
     * Se só existir o @LoadBalanced, o cliente Eureka tenta resolver 127.0.0.1 como serviceId.
     */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    PlanoLeituraClient planoLeituraClient(
            @Value("${lambda.base-url:http://localhost:9000}") String baseUrl) {
        // Builder sem @LoadBalanced: URL absoluta da Lambda / Function URL
        RestClient client = RestClient.builder().baseUrl(baseUrl).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build();
        return factory.createClient(PlanoLeituraClient.class);
    }
}
