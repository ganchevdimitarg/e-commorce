
package com.concordeu.gateway.config;

import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class SwaggerConfig {

    private final DiscoveryClient discoveryClient;

    public SwaggerConfig(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Bean
    @Primary
    public SwaggerUiConfigProperties swaggerUiConfigProperties() {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();

        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = new HashSet<>();

        // Get all registered services from Eureka
        discoveryClient.getServices().forEach(serviceId -> {
            // Filters services; builds URL for each non‑gateway service
            if (!serviceId.equalsIgnoreCase("gateway") &&
                    !serviceId.equalsIgnoreCase("eureka-server")) {

                String url = String.format("/%s/v3/api-docs", serviceId.toLowerCase());
                urls.add(new AbstractSwaggerUiConfigProperties.SwaggerUrl(
                        serviceId,
                        url,
                        serviceId
                ));
            }
        });

        properties.setUrls(urls);
        return properties;
    }
}