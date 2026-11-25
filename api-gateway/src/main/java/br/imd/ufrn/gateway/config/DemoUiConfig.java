package br.imd.ufrn.gateway.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class DemoUiConfig {

    @Bean
    RouterFunction<ServerResponse> demoUiRoutes() {
        return RouterFunctions.route()
                .GET("/", request -> ServerResponse.temporaryRedirect(URI.create("/demo/index.html")).build())
                .GET("/demo", request -> ServerResponse.temporaryRedirect(URI.create("/demo/index.html")).build())
                .GET("/demo/", request -> ServerResponse.temporaryRedirect(URI.create("/demo/index.html")).build())
                .resources("/demo/**", new ClassPathResource("static/demo/"))
                .build();
    }
}
