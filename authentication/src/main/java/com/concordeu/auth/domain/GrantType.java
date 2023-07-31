package com.concordeu.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.uuid.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.Set;
import java.util.UUID;

@Entity(name = "GrantTypes")
@Table(name = "grant_types")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class GrantType {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", type = UuidGenerator.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "grant_type_id", length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
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
