package com.concordeu.profile;

import com.concordeu.profile.entities.Profile;
import com.concordeu.profile.repositories.ProfileRepository;
import com.concordeu.profile.security.UserPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static com.concordeu.profile.security.UserPermission.*;

@Component
@RequiredArgsConstructor
public class BoostrapData implements CommandLineRunner {
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    public void run(String... args) throws Exception {
        if (profileRepository.count().toFuture().get() == 0) {
            Profile admin = Profile.builder()
                    .username("admin@gmail.com")
                    .password(passwordEncoder.encode("admin"))
                    .grantedAuthorities(
                            Set.of(new SimpleGrantedAuthority(CATALOG_READ.getPermission()),
                                    new SimpleGrantedAuthority(CATALOG_WRITE.getPermission()),
                                    new SimpleGrantedAuthority(PROFILE_READ.getPermission()),
                                    new SimpleGrantedAuthority(PROFILE_WRITE.getPermission()),
                                    new SimpleGrantedAuthority(ORDER_READ.getPermission()),
                                    new SimpleGrantedAuthority(ORDER_WRITE.getPermission()),
                                    new SimpleGrantedAuthority(NOTIFICATION_READ.getPermission()),
                                    new SimpleGrantedAuthority(NOTIFICATION_WRITE.getPermission())
                            )
                    )
                    .build();

            Profile user = Profile.builder()
                    .username("user@gmail.com")
                    .password(passwordEncoder.encode("user"))
                    .grantedAuthorities(
                            Set.of(new SimpleGrantedAuthority(CATALOG_READ.getPermission()),
                                    new SimpleGrantedAuthority(PROFILE_READ.getPermission()),
                                    new SimpleGrantedAuthority(PROFILE_WRITE.getPermission()),
                                    new SimpleGrantedAuthority(ORDER_READ.getPermission()),
                                    new SimpleGrantedAuthority(ORDER_WRITE.getPermission()),
                                    new SimpleGrantedAuthority(NOTIFICATION_READ.getPermission()),
                                    new SimpleGrantedAuthority(NOTIFICATION_WRITE.getPermission())
                            )
                    )
                    .build();

            profileRepository.saveAll(List.of(admin, user)).subscribe();
        }
    }
}
