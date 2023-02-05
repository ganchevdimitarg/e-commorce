package com.concordeu.auth.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity(name = "Clients")
@Table(name = "clients")
@NamedEntityGraph(
        name = "graph-auth-client",
        attributeNodes = {
                @NamedAttributeNode(value = "redirectUri", subgraph = "client-redirectUri"),
                @NamedAttributeNode(value = "grantType", subgraph = "client-grantType"),
                @NamedAttributeNode(value = "scope", subgraph = "client-scope"),
                @NamedAttributeNode(value = "tokenSettings", subgraph = "client-tokenSettings")
        },
        subgraphs = {
                @NamedSubgraph(name = "client-redirectUri", attributeNodes = @NamedAttributeNode("client")),
                @NamedSubgraph(name = "client-grantType", attributeNodes = @NamedAttributeNode("client")),
                @NamedSubgraph(name = "client-scope", attributeNodes = @NamedAttributeNode("client")),
                @NamedSubgraph(name = "client-tokenSettings", attributeNodes = @NamedAttributeNode("client"))
        })
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Client {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "client_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "client_id_name",nullable = false)
    private String clientId;
    @Column(name = "client_secret", nullable = false)
    private String clientSecret;
    @Column(name = "auth_method", nullable = false)
    private String authMethod;
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<RedirectUri> redirectUri;
    @ManyToMany(mappedBy = "client")
    private Set<GrantType> grantType;
    @ManyToMany(mappedBy = "client")
    private Set<Scope> scope;
    @ManyToMany(mappedBy = "client")
    private Set<TokenSetting> tokenSettings;
}
