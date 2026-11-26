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

    /** Builder sem load balancer (URLs absolutas / clientes internos). */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /** Builder com load balancer (service discovery via Eureka). */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:45s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    PlanoLeituraClient planoLeituraClient(
            @Value("${lambda.base-url:http://localhost:9000}") String baseUrl,
            @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:45s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build();
        return factory.createClient(PlanoLeituraClient.class);
    }
}
