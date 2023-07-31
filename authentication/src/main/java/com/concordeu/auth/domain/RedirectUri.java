package com.concordeu.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.uuid.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity(name = "RedirectUris")
@Table(name = "redirect_uris")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class RedirectUri {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", type = UuidGenerator.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "redirect_uri_id", length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
}
