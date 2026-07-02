package com.ganchevdimitarg.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.UUID;

@Entity(name = "Scopes")
@Table(name = "scopes")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Setter
@Getter
public class Scope extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "scope_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "scope", nullable = false)
    private String scopeName;
    @ManyToMany
    @JoinTable(
            name = "clients_scopes",
            joinColumns = @JoinColumn(name = "scope_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    private Set<Client> client;
}
