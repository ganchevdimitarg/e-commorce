package com.concordeu.profile.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        servers = {@Server(url = "https://localhost:8081")},
        info = @Info(title = "Profile Service APIs",
                description = "This lists all the Profile Service API Calls. The Calls are OAuth2 secured, so please use your client ID and Secret to test them out.",
                version = "v1.0"))
@SecurityScheme(
        name = "security_auth",
        type = SecuritySchemeType.OAUTH2,
        in = SecuritySchemeIn.HEADER,
        flows = @OAuthFlows(authorizationCode = @OAuthFlow(tokenUrl = "http://localhost:8082/oauth2/token",
                scopes = {@OAuthScope(name = "openid", description = "openid scope")},
                authorizationUrl = "http://localhost:8082/oauth2/authorize")))
public class OpenAPI3Configuration {
}
