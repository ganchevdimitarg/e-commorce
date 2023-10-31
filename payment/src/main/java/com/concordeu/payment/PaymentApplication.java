package com.concordeu.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableKafka
public class PaymentApplication {
    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(PaymentApplication.class, args);
    }
}
