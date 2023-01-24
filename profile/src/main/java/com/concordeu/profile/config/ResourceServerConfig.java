package com.concordeu.profile.config;

import com.concordeu.client.introspector.CustomOpaqueTokenIntrospector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import javax.servlet.http.HttpServletRequest;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    private final JwtDecoder jwtDecoder;
    private final CustomLogoutHandler logoutHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                    .mvcMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .mvcMatchers("/actuator/**").permitAll()
                    .mvcMatchers("/api/v1/profile/register-admin").permitAll()
                    .mvcMatchers("/api/v1/profile/register-worker").permitAll()
                    .mvcMatchers("/api/v1/profile/register-user").permitAll()
                    .mvcMatchers("/api/v1/profile/password-reset").permitAll()
                    .anyRequest().authenticated()
                .and()
                .logout()
                    .logoutUrl("/api/v1/profile/logout")
                    .addLogoutHandler(logoutHandler)
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
                    .permitAll()
                .and()
                .oauth2ResourceServer()
                    .authenticationManagerResolver(this.tokenAuthenticationManagerResolver());

        return http.build();
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        return new CustomOpaqueTokenIntrospector();
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver() {
        AuthenticationManager jwt = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
        AuthenticationManager opaqueToken = new ProviderManager(
                new OpaqueTokenAuthenticationProvider(opaqueTokenIntrospector()));
        return (request) -> isJwt(request) ? jwt : opaqueToken;
    }

    private boolean isJwt(HttpServletRequest request) {
        try {
            jwtDecoder.decode(request
                    .getHeader("Authorization")
                    .replace("Bearer ", "")
            );
            return true;
        } catch (BadJwtException e) {
            log.debug(e.getMessage());
            return false;
        }
    }
}