package com.concordeu.order.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        servers = {@Server(url = "https://localhost:8081")},
        info = @Info(
                title = "Order Service APIs",
                description = "This lists all the Order Service API Calls. The Calls are OAuth2 secured, so please use your client ID and Secret to test them out.",
                version = "v3.0.1"))
@SecurityRequirement(name = "security_auth")
@SecurityScheme(
        name = "security_auth",
        type = SecuritySchemeType.OAUTH2,
        in = SecuritySchemeIn.HEADER,
        bearerFormat = "jwt",
        flows = @OAuthFlows(
                authorizationCode = @OAuthFlow(
                        authorizationUrl = "${springdoc.oAuthFlow.authorizationUrl}",
                        tokenUrl = "${springdoc.oAuthFlow.tokenUrl}",
                        scopes = {
                                @OAuthScope(name = "openid", description = "openid scope"),
                                @OAuthScope(name = "order.read", description = "with this scope, the user can access every get request"),
                                @OAuthScope(name = "order.write", description = "with this scope, the user can access every post, put and delete request"),
                        }
                )
        )
)
public class OpenAPI3Config {
}

