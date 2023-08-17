package com.concordeu.auth.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Document("registered_clients")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Client {
    @Id
    private String id;
    private String clientId;
    private String clientSecret;
    private String authMethod;
    private Set<RedirectUri> redirectUri;
    private Set<GrantType> grantType;
    private Set<Scope> scope;
    private Set<TokenSetting> tokenSettings;
}
