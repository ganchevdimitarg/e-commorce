package com.concordeu.auth.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity(name = "Scopes")
@Table(name = "scopes")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Scope {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "scope_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "scope", nullable = false)
    private String scope;
    @ManyToMany
    @JoinTable(
            name = "clients_scopes",
            joinColumns = @JoinColumn(name = "scope_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    private Set<Client> client;
}
