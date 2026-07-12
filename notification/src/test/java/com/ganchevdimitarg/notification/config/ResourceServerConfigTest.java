package com.ganchevdimitarg.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServerConfigTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private HttpServletRequest request;

    @Test
    void should_returnFalse_when_authorizationHeaderMissing() {
        when(request.getHeader("Authorization")).thenReturn(null);

        ResourceServerConfig config = new ResourceServerConfig(jwtDecoder, null, null);
        boolean result = ReflectionTestUtils.invokeMethod(config, "isJwt", request);

        assertThat(result).isFalse();
    }

    @Test
    void should_returnFalse_when_authorizationHeaderBlank() {
        when(request.getHeader("Authorization")).thenReturn("   ");

        ResourceServerConfig config = new ResourceServerConfig(jwtDecoder, null, null);
        boolean result = ReflectionTestUtils.invokeMethod(config, "isJwt", request);

        assertThat(result).isFalse();
    }
}
