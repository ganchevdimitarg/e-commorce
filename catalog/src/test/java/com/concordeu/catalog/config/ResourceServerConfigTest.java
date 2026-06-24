package com.concordeu.catalog.config;

import com.concordeu.catalog.exception.ProblemAccessDeniedHandler;
import com.concordeu.catalog.exception.ProblemAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ResourceServerConfigTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final ProblemAuthenticationEntryPoint authEntryPoint = mock(ProblemAuthenticationEntryPoint.class);
    private final ProblemAccessDeniedHandler accessDeniedHandler = mock(ProblemAccessDeniedHandler.class);
    private final ResourceServerConfig config = new ResourceServerConfig(jwtDecoder, authEntryPoint, accessDeniedHandler);

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
    void should_routeToJwt_withoutDecoding_when_tokenIsThreeSegmentJose() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // header: {"alg":"none"} base64url = eyJhbGciOiJub25lIn0
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.sig");

        boolean result = config.isJwt(request);

        assertThat(result).isTrue();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void should_routeToOpaque_when_tokenIsNotJose() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer opaque-abc123");

        assertThat(config.isJwt(request)).isFalse();
        verifyNoInteractions(jwtDecoder);
    }
}
