package com.concordeu.security;

import com.concordeu.dto.AuthUserDto;
import com.concordeu.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2UserSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AuthService authService;

   /* public OAuth2UserSuccessHandler(AuthService authService) {
        this.authService = authService;
        setDefaultTargetUrl("/home");
    }*/

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) authentication;

            String email = oAuth2AuthenticationToken.getPrincipal().getAttribute("email");

            AuthUserDto user = authService.getOrCreateUser(email);
            UserDetails userDetails = authService.loadUserByUsername(user.getEmail());
            authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
