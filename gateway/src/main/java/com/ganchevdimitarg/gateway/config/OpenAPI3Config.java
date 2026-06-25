package com.ganchevdimitarg.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPI3Config {

    @Value("${spring.security.oauth2.client.provider.spring.authorization-uri}")
    private String authorizationUrl;

    @Value("${spring.security.oauth2.client.provider.spring.token-uri}")
    private String tokenUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        // Configures API documentation with metadata and development server
                // Configures API metadata: title, version, description, contact
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Gateway API")
                        .version("0.0.2")
                        .description("API Gateway for E-Commerce Microservices")
                        .contact(new Contact()
                                .name("Dimitar Ganchev")
                                .email("ganchevdimitarg@gmail.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Development")
//                        new Server().url("https://api.concordeu.com").description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                // Defines OAuth2 authorization code flow with scopes
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authorizationUrl)
                                                .tokenUrl(tokenUrl)
                                                // Defines OAuth2 scopes for various data access
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect scope")
                                                        .addString("catalog.read", "Read catalog data")
                                                        .addString("catalog.write", "Write catalog data")
                                                        .addString("profile.read", "Read profile data")
                                                        .addString("profile.write", "Write profile data")
                                                        .addString("order.read", "Read order data")
                                                        .addString("order.write", "Write order data")
                                                        .addString("notification.read", "Read notifications")
                                                        .addString("notification.write", "Write notifications"))))))
                .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }
}