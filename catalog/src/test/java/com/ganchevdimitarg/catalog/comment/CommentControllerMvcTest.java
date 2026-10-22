package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.config.ResourceServerConfig;
import com.ganchevdimitarg.catalog.controller.CommentController;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAuthenticationEntryPoint;
import com.ganchevdimitarg.catalog.ratelimit.RateLimiterCache;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = CommentController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class,
        // The web slice excludes plain @Component beans, so the cache and registry are explicit.
        RateLimiterCache.class, SimpleMeterRegistry.class})
class CommentControllerMvcTest {

    private static final UUID COMMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    CommentService commentService;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    Tracer tracer; // MdcRequestFilter dependency, not exercised by web-slice tests

    @SuppressWarnings("unchecked") // Mockito generic erasure on mock(ValueOperations.class)
    @BeforeEach
    void setUpRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void should_return201AndCommentLocation_when_createCommentWithReviewScope() throws Exception {
        CommentResponseDto response = new CommentResponseDto(
                COMMENT_ID, "Great product", "This is a thorough review text", 5, "john",
                PRODUCT_ID, "mouse");
        when(commentService.createComment(any(CreateCommentCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-c1")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 5,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/catalog/comments/" + COMMENT_ID))
                .andExpect(jsonPath("$.id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("Great product"))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.productName").value("mouse"));
    }

    @Test
    void should_return409ProblemJson_when_serviceRejectsDuplicateReview() throws Exception {
        doThrow(new ConflictException("duplicate review"))
                .when(commentService).createComment(any(CreateCommentCommand.class));

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-duplicate-review")
                        .content("""
                                {"title":"Great product","text":"This is a thorough review text","star":5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void should_return204_when_deleteCommentWithReviewScope() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/comments/{id}", COMMENT_ID)
                        .with(jwt().jwt(token -> token.subject("user-123"))
                                .authorities(() -> "SCOPE_catalog.review"))
                        .header("Idempotency-Key", "k-del-1"))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(COMMENT_ID);
    }

    @Test
    void should_return400ProblemJson_when_deleteCommentWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/comments/{id}", COMMENT_ID)
                        .with(jwt().jwt(token -> token.subject("user-123"))
                                .authorities(() -> "SCOPE_catalog.review")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return403ProblemJson_when_deleteCommentIsDenied() throws Exception {
        doThrow(new AccessDeniedException("not the author"))
                .when(commentService).deleteComment(COMMENT_ID);

        mockMvc.perform(delete("/api/v1/catalog/comments/{id}", COMMENT_ID)
                        .with(jwt().jwt(token -> token.subject("user-123"))
                                .authorities(() -> "SCOPE_catalog.review"))
                        .header("Idempotency-Key", "k-del-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_return200WithAvgStars_when_getAvgStarsWithReadScope() throws Exception {
        when(commentService.getAvgStars("mouse")).thenReturn(4.5);

        mockMvc.perform(get("/api/v1/catalog/products/mouse/comments/avg-stars")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().string("4.5"));
    }

    @Test
    void should_return200_when_findAllByProductNameWithReadScope() throws Exception {
        CommentResponseDto dto = new CommentResponseDto(
                COMMENT_ID, "Great", "Good product review", 5, "john", PRODUCT_ID, "mouse");
        Page<CommentResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(commentService.findAllByProductNameByPage(eq("mouse"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Great"));
    }

    @Test
    void should_return200_when_findAllByAuthorWithReadScope() throws Exception {
        CommentResponseDto dto = new CommentResponseDto(
                COMMENT_ID, "Great", "Good product review", 5, "john", PRODUCT_ID, "mouse");
        Page<CommentResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(commentService.findAllByAuthorByPage(eq("john"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/comments/by-author/john")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("john"));
    }

    @Test
    void should_return400ProblemJson_when_sortPropertyIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/comments/by-author/alice?sort=doesNotExist")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400ProblemJson_when_createCommentWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-c2")
                        .content("""
                                {
                                    "title": "ab",
                                    "text": "short",
                                    "star": 5,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_createCommentWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 5,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400ProblemJson_when_starAboveRange() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-star-high")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 6,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_starBelowRange() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-star-low")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 0,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return201_when_starIsFractionalWithinRange() throws Exception {
        CommentResponseDto response = new CommentResponseDto(
                COMMENT_ID, "Great product", "This is a thorough review text", 4.5, "john",
                PRODUCT_ID, "mouse");
        when(commentService.createComment(any(CreateCommentCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-star-fractional")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 4.5,
                                    "author": "john"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.star").value(4.5));
    }

    @Test
    void should_return404ProblemJson_when_getAvgStarsForNonexistentProduct() throws Exception {
        when(commentService.getAvgStars("nonexistent"))
                .thenThrow(new NotFoundException("Product", "nonexistent"));

        mockMvc.perform(get("/api/v1/catalog/products/nonexistent/comments/avg-stars")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Product not found: nonexistent"));
    }

    @Test
    void should_useAuthenticatedPrincipalAsAuthor_when_requestBodySuppliesDifferentAuthor() throws Exception {
        CommentResponseDto response = new CommentResponseDto(
                COMMENT_ID, "Great product", "This is a thorough review text", 5, "user-123",
                PRODUCT_ID, "mouse");
        when(commentService.createComment(any(CreateCommentCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().jwt(token -> token.subject("user-123"))
                                .authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-author-spoof")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 5,
                                    "author": "someone-else"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCommentCommand> captor = ArgumentCaptor.forClass(CreateCommentCommand.class);
        verify(commentService).createComment(captor.capture());
        assertThat(captor.getValue().author()).isEqualTo("user-123");
        assertThat(captor.getValue().productName()).isEqualTo("mouse");
    }

    @Test
    void should_useAuthenticatedPrincipalAsAuthor_when_requestBodyOmitsAuthor() throws Exception {
        CommentResponseDto response = new CommentResponseDto(
                COMMENT_ID, "Great product", "This is a thorough review text", 5, "user-456",
                PRODUCT_ID, "mouse");
        when(commentService.createComment(any(CreateCommentCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().jwt(token -> token.subject("user-456"))
                                .authorities(() -> "SCOPE_catalog.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-author-omitted")
                        .content("""
                                {
                                    "title": "Great product",
                                    "text": "This is a thorough review text",
                                    "star": 5
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCommentCommand> captor = ArgumentCaptor.forClass(CreateCommentCommand.class);
        verify(commentService).createComment(captor.capture());
        assertThat(captor.getValue().author()).isEqualTo("user-456");
    }
}
