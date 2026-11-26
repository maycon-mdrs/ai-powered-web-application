package br.imd.ufrn.ia.config;

import org.springframework.stereotype.Component;

import graphql.introspection.GoodFaithIntrospection;
import jakarta.annotation.PostConstruct;

/** Libera introspecção completa para o GraphiQL */
@Component
public class GraphQlLabConfig {

    @PostConstruct
    void allowGraphiqlIntrospection() {
        GoodFaithIntrospection.enabledJvmWide(false);
    }
}
