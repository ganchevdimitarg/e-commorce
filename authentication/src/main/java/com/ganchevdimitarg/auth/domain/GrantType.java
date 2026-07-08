package com.ganchevdimitarg.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.UUID;

@Entity(name = "GrantTypes")
@Table(name = "grant_types")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Setter
@Getter
public class GrantType extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grant_type_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "grant_type", nullable = false)
    private String grantType;
    @ManyToMany
    @JoinTable(
            name = "clients_grant_types",
            joinColumns = @JoinColumn(name = "grant_type_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    private Set<Client> client;
}
