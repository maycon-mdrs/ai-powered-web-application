package br.imd.ufrn.coordenador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MsCoordenadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCoordenadorApplication.class, args);
    }
}
