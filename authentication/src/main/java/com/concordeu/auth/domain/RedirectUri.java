package com.concordeu.auth.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
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
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "redirect_uri_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
}
