package com.concordeu.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.uuid.UuidGenerator;

import java.util.Set;
import java.util.UUID;

import static org.hibernate.type.SqlTypes.CHAR;

@Entity(name = "Scopes")
@Table(name = "scopes")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Scope {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", type = UuidGenerator.class)
    @JdbcTypeCode(CHAR)
    @Column(name = "scope_id", length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
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
