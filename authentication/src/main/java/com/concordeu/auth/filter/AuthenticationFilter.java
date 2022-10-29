package com.concordeu.auth.filter;

import com.concordeu.auth.config.security.JwtConfiguration;
import com.concordeu.auth.config.security.JwtSecretKey;
import com.concordeu.auth.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JwtConfiguration jwtConfiguration;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        log.info("Username is: {}", username);
        log.info("Password is: {}", password);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(authenticationToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain,
                                            Authentication authentication) throws IOException, ServletException {

        User user = (User) authentication.getPrincipal();
        String access_token = jwtTokenUtil.generateJwtToken(request, user, 2);
        String refresh_token = jwtTokenUtil.generateJwtToken(request, user, 4);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", jwtConfiguration.getTokenPrefix() + access_token);
        tokens.put("refresh_token", jwtConfiguration.getTokenPrefix() + refresh_token);
        response.setContentType(APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(), tokens);
        log.info("Successful authenticated user: {}", user.getUsername());
    }


}
