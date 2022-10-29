package com.concordeu.auth.config.security;

import com.concordeu.auth.filter.AuthenticationFilter;
import com.concordeu.auth.filter.AuthorizationFilter;
import com.concordeu.auth.security.OAuth2UserSuccessHandler;
import com.concordeu.auth.service.securiy.SecurityService;
import com.concordeu.auth.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.concurrent.TimeUnit;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    private final PasswordEncoder passwordEncoder;
    private final SecurityService securityService;
    private final OAuth2UserSuccessHandler oAuth2UserSuccessHandler;
    private final JwtConfiguration jwtConfiguration;
    private final JwtSecretKey jwtSecretKey;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(STATELESS)
                .and()
                .addFilter(new AuthenticationFilter(authenticationManager(), jwtConfiguration, jwtTokenUtil))
                .addFilterAfter(new AuthorizationFilter(jwtConfiguration, jwtSecretKey, jwtTokenUtil), UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests()
                .antMatchers("/",
                        "/index",
                        "/login",
                        "/api/v1/profile/**",
                        "/css/*",
                        "/js/*",
                        "/swagger-ui").permitAll()
                .anyRequest()
                .authenticated();
                /*.and()
                .formLogin()
                .and()
                .rememberMe()
                .tokenValiditySeconds((int) TimeUnit.DAYS.toSeconds(20))
                .and()
                .logout()
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .logoutSuccessUrl("/login?logout")
                .and()
                .oauth2Login()
                .successHandler(oAuth2UserSuccessHandler);*/

    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(daoAuthenticationProvider());
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(securityService);
        return provider;
    }
}
