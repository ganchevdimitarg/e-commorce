package com.concordeu.catalog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceServerConfigTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final ResourceServerConfig config = new ResourceServerConfig(jwtDecoder);

    @Test
    void should_returnFalse_when_authorizationHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(config.isJwt(request)).isFalse();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void should_returnFalse_when_authorizationHeaderBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "   ");

        assertThat(config.isJwt(request)).isFalse();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void should_delegateToDecoder_when_bearerTokenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        when(jwtDecoder.decode("abc.def.ghi")).thenReturn(mock(Jwt.class));

        assertThat(config.isJwt(request)).isTrue();
        verify(jwtDecoder).decode("abc.def.ghi");
    }
}
