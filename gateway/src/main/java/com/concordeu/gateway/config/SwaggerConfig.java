package com.concordeu.gateway.config;

import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Primary
public class SwaggerConfig implements SwaggerResourcesProvider {

    public static final String API_URI = "/v3/api-docs";

    private final RouteDefinitionLocator routeLocator;

    public SwaggerConfig(RouteDefinitionLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public List<SwaggerResource> get() {
        List<SwaggerResource> resources = new ArrayList<>();
        routeLocator.getRouteDefinitions().subscribe(
                routeDefinition -> {
                    String resourceName = routeDefinition.getId()
                            .replace("ReactiveCompositeDiscoveryClient_", "");
                    if (resourceName.equals(resourceName.toLowerCase())) {
                        String location = routeDefinition
                                .getPredicates()
                                .get(0)
                                .getArgs()
                                .get("_genkey_0")
                                .replace("/**", API_URI);
                        resources.add(swaggerResource(resourceName, location));
                    }
                }
        );
        return resources;
    }

    private SwaggerResource swaggerResource(String name, String location) {
        SwaggerResource swaggerResource = new SwaggerResource();
        swaggerResource.setName(name);
        swaggerResource.setLocation(location);
        swaggerResource.setSwaggerVersion("2.0");
        return swaggerResource;
    }
}
