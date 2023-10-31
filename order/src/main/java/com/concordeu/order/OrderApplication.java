package com.concordeu.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableR2dbcAuditing
public class OrderApplication {
    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(OrderApplication.class, args);
    }
}
