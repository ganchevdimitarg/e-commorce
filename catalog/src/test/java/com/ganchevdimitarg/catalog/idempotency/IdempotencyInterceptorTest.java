package com.ganchevdimitarg.catalog.idempotency;

import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import com.ganchevdimitarg.catalog.idempotency.IdempotencyStore.ClaimResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    private static final String URI = "/api/v1/catalog/products";
    private static final String CLIENT_KEY = "k1";
    private static final String SCOPED_KEY = IdempotencyKeys.scopedKey("alice", "POST", URI, CLIENT_KEY);

    @Mock
    IdempotencyStore store;

    IdempotencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyInterceptor(store);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", "n/a", "SCOPE_catalog.write"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_claimAndProceed_when_keyIsNew() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());
        MockHttpServletRequest request = writeRequest();

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(IdempotencyInterceptor.REDIS_KEY_ATTRIBUTE)).isEqualTo(SCOPED_KEY);
    }

    @Test
    void should_throwConflict_when_claimIsInProgress() {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.InProgress());
        MockHttpServletRequest request = writeRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("in progress");
    }

    @Test
    void should_replayStoredResponse_when_claimIsCompleted() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Completed(
                IdempotencyRecord.completed(201, "application/json",
                        "/api/v1/catalog/products/1", "{\"id\":1}")));
        MockHttpServletRequest request = writeRequest();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());

        assertThat(interceptor.preHandle(request, wrapper, new Object())).isFalse();

        assertThat(wrapper.getStatus()).isEqualTo(201);
        assertThat(wrapper.getContentType()).isEqualTo("application/json");
        assertThat(wrapper.getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/catalog/products/1");
        assertThat(body(wrapper)).isEqualTo("{\"id\":1}");
        assertThat(request.getAttribute(IdempotencyInterceptor.REDIS_KEY_ATTRIBUTE)).isNull();
    }

    @Test
    void should_replayUtf8BodyByteExactly_when_storedBodyContainsNonAscii() throws Exception {
        // text/plain keeps MockHttpServletResponse on its ISO-8859-1 default (it special-cases
        // JSON to UTF-8), which is exactly the container fallback that byte-exact replay must
        // not depend on.
        String body = "{\"name\":\"мышка\"}";
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Completed(
                IdempotencyRecord.completed(201, "text/plain", null, body)));
        MockHttpServletRequest request = writeRequest();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());

        assertThat(interceptor.preHandle(request, wrapper, new Object())).isFalse();

        assertThat(wrapper.getContentAsByteArray()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_throwValidation_when_writeMissingIdempotencyKey() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URI);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idempotency-Key");
        verifyNoInteractions(store);
    }

    @Test
    void should_throwValidation_when_writeBlankIdempotencyKey() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URI);
        request.addHeader("Idempotency-Key", "  ");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idempotency-Key");
        verifyNoInteractions(store);
    }

    @Test
    void should_skip_when_getRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", URI);
        request.addHeader("Idempotency-Key", CLIENT_KEY);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(store);
    }

    @Test
    void should_skipBatchGet_withoutIdempotencyKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URI + ":batch-get");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(store);
    }

    @Test
    void should_useScopedKey_when_claiming() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());

        interceptor.preHandle(writeRequest(), new MockHttpServletResponse(), new Object());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(store).claim(key.capture());
        assertThat(key.getValue()).isEqualTo(IdempotencyKeys.scopedKey("alice", "POST", URI, CLIENT_KEY));
    }

    @Test
    void should_releaseKey_when_responseStatusIs5xx() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        response.setStatus(500);
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(store).release(SCOPED_KEY);
    }

    @Test
    void should_releaseKey_when_exceptionEscapes() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        response.setStatus(200);
        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        verify(store).release(SCOPED_KEY);
    }

    @Test
    void should_completeKey_when_responseStatusIs4xx() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());
        MockHttpServletRequest request = writeRequest();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        interceptor.preHandle(request, wrapper, new Object());

        String problem = "{\"code\":\"VALIDATION_ERROR\"}";
        wrapper.setStatus(400);
        wrapper.setContentType("application/problem+json");
        wrapper.getWriter().write(problem);

        interceptor.afterCompletion(request, wrapper, new Object(), null);

        verify(store).complete(SCOPED_KEY,
                IdempotencyRecord.completed(400, "application/problem+json", null, problem));
    }

    @Test
    void should_completeKey_when_responseStatusIs2xx() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Claimed());
        MockHttpServletRequest request = writeRequest();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        interceptor.preHandle(request, wrapper, new Object());

        String body = "{\"id\":1,\"name\":\"mouse\"}";
        wrapper.setStatus(201);
        wrapper.setContentType("application/json");
        wrapper.setHeader(HttpHeaders.LOCATION, "/api/v1/catalog/products/1");
        wrapper.getWriter().write(body);

        interceptor.afterCompletion(request, wrapper, new Object(), null);

        verify(store).complete(SCOPED_KEY, IdempotencyRecord.completed(
                201, "application/json", "/api/v1/catalog/products/1", body));
    }

    @Test
    void should_proceedWithoutClaim_when_storeUnavailable() throws Exception {
        when(store.claim(anyString()))
                .thenThrow(new IdempotencyUnavailableException("redis down", new RuntimeException("connection refused")));
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        // Fail-open must not leave a key behind: afterCompletion is a no-op without the attribute.
        interceptor.afterCompletion(request, response, new Object(), null);
        verify(store).claim(SCOPED_KEY);
        verify(store, never()).release(anyString());
        verify(store, never()).complete(anyString(), any());
    }

    @Test
    void should_notComplete_when_replayReturnedFalse() throws Exception {
        when(store.claim(SCOPED_KEY)).thenReturn(new ClaimResult.Completed(
                IdempotencyRecord.completed(200, "application/json", null, "{}")));
        MockHttpServletRequest request = writeRequest();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());

        assertThat(interceptor.preHandle(request, wrapper, new Object())).isFalse();
        interceptor.afterCompletion(request, wrapper, new Object(), null);

        verify(store).claim(SCOPED_KEY);
        verify(store, never()).complete(anyString(), any());
    }

    private MockHttpServletRequest writeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URI);
        request.addHeader("Idempotency-Key", CLIENT_KEY);
        return request;
    }

    private static String body(ContentCachingResponseWrapper wrapper) {
        return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
