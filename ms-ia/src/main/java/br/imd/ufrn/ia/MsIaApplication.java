package br.imd.ufrn.ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MsIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsIaApplication.class, args);
    }
}
