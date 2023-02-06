package com.concordeu.auth.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity(name = "TokenSettings")
@Table(name = "token_settings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class TokenSetting {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "token_setting_id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "access_token_time_to_live")
    private long accessTokenTimeToLive;
    @Column(name = "refresh_token_time_to_live")
    private long refreshTokenTimeToLive;
    @ManyToMany
    @JoinTable(
            name = "clients_token_settings",
            joinColumns = @JoinColumn(name = "token_setting_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    private Set<Client> client;
}
