package com.concordeu.profile;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
@OpenAPIDefinition(info =
@Info(title = "Profile API", description = "Documentation Profile API v1.0")
)
public class ProfileApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProfileApplication.class, args);
    }
}
