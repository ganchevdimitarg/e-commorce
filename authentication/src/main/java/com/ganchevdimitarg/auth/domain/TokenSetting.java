package com.ganchevdimitarg.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.UUID;

@Entity(name = "TokenSettings")
@Table(name = "token_settings")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Setter
@Getter
public class TokenSetting extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_setting_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "access_token_time_to_live", nullable = false)
    private long accessTokenTimeToLive;
    @Column(name = "refresh_token_time_to_live", nullable = false)
    private long refreshTokenTimeToLive;
    @ManyToMany
    @JoinTable(
            name = "clients_token_settings",
            joinColumns = @JoinColumn(name = "token_setting_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    private Set<Client> client;
}
