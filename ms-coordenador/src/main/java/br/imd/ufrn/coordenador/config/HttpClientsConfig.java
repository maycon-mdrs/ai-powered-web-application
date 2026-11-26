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
import io.micrometer.observation.ObservationRegistry;

@Configuration
public class HttpClientsConfig {

    /**
     * RestClient instrumentado: propaga W3C/B3 e cria spans CLIENT no Zipkin
     * (MS-IA GraphQL e Lambda Function URL).
     */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder(
            ObservationRegistry observationRegistry,
            @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:90s}") Duration readTimeout) {
        return instrumentedBuilder(observationRegistry, connectTimeout, readTimeout);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(
            ObservationRegistry observationRegistry,
            @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:90s}") Duration readTimeout) {
        return instrumentedBuilder(observationRegistry, connectTimeout, readTimeout);
    }

    @Bean
    PlanoLeituraClient planoLeituraClient(
            ObservationRegistry observationRegistry,
            @Value("${lambda.base-url:http://localhost:9000}") String baseUrl,
            @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:90s}") Duration readTimeout) {
        RestClient client = instrumentedBuilder(observationRegistry, connectTimeout, readTimeout)
                .baseUrl(baseUrl)
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build();
        return factory.createClient(PlanoLeituraClient.class);
    }

    private static RestClient.Builder instrumentedBuilder(
            ObservationRegistry observationRegistry,
            Duration connectTimeout,
            Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .observationRegistry(observationRegistry);
    }
}
