package com.concordeu.profile;

import com.concordeu.profile.entities.Profile;
import com.concordeu.client.common.ProfileGrantedAuthority;
import com.concordeu.profile.repositories.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
                            Set.of(new ProfileGrantedAuthority(CATALOG_READ.getPermission()),
                                    new ProfileGrantedAuthority(CATALOG_WRITE.getPermission()),
                                    new ProfileGrantedAuthority(PROFILE_READ.getPermission()),
                                    new ProfileGrantedAuthority(PROFILE_WRITE.getPermission()),
                                    new ProfileGrantedAuthority(ORDER_READ.getPermission()),
                                    new ProfileGrantedAuthority(ORDER_WRITE.getPermission()),
                                    new ProfileGrantedAuthority(NOTIFICATION_READ.getPermission()),
                                    new ProfileGrantedAuthority(NOTIFICATION_WRITE.getPermission())
                            )
                    )
                    .build();

            Profile user = Profile.builder()
                    .username("user@gmail.com")
                    .password(passwordEncoder.encode("user"))
                    .grantedAuthorities(
                            Set.of(new ProfileGrantedAuthority(CATALOG_READ.getPermission()),
                                    new ProfileGrantedAuthority(PROFILE_READ.getPermission()),
                                    new ProfileGrantedAuthority(PROFILE_WRITE.getPermission()),
                                    new ProfileGrantedAuthority(ORDER_READ.getPermission()),
                                    new ProfileGrantedAuthority(ORDER_WRITE.getPermission()),
                                    new ProfileGrantedAuthority(NOTIFICATION_READ.getPermission()),
                                    new ProfileGrantedAuthority(NOTIFICATION_WRITE.getPermission())
                            )
                    )
                    .build();

            profileRepository.saveAll(List.of(admin, user)).subscribe();
        }
    }
}
