package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.config.ResourceServerConfig;
import com.ganchevdimitarg.catalog.controller.CommentController;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAuthenticationEntryPoint;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = CommentController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class CommentControllerMvcTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    CommentService commentService;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CircuitBreakerRegistry circuitBreakerRegistry;
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
    void should_return201AndLocation_when_createCommentWithWriteScope() throws Exception {
        CommentResponseDto response = new CommentResponseDto(
                "Great product", "This is a thorough review text", 5, "john", null);
        when(commentService.createComment(any(CreateCommentCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
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
                .andExpect(header().string("Location", "/api/v1/catalog/products/mouse/comments"))
                .andExpect(jsonPath("$.title").value("Great product"));
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
                "Great", "Good product review", 5, "john", null);
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
                "Great", "Good product review", 5, "john", null);
        Page<CommentResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(commentService.findAllByAuthorByPage(eq("john"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/comments/by-author/john")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("john"));
    }

    @Test
    void should_return400ProblemJson_when_createCommentWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
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
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
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
}
