package com.concordeu.catalog.comment;

import com.concordeu.catalog.config.ResourceServerConfig;
import com.concordeu.catalog.controller.CommentController;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.exception.ControllerExceptionHandler;
import com.concordeu.catalog.exception.ProblemAccessDeniedHandler;
import com.concordeu.catalog.exception.ProblemAuthenticationEntryPoint;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.concordeu.catalog.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    MapStructMapper mapper;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CircuitBreakerRegistry circuitBreakerRegistry;
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void should_return200WithAvgStars_when_getAvgStarsWithReadScope() throws Exception {
        when(commentService.getAvgStars("mouse")).thenReturn(4.5);

        mockMvc.perform(get("/api/v1/catalog/comment/get-avg-stars")
                        .param("productName", "mouse")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().string("4.5"));
    }

    @Test
    void should_return200_when_createCommentWithValidBody() throws Exception {
        CommentResponseDto response = new CommentResponseDto("Great product", "This is a thorough review text", 5, "john", null);
        when(mapper.mapCommentRequestDtoToCommentResponseDto(any())).thenReturn(response);
        when(commentService.createComment(any(), eq("mouse"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/comment/create-comment")
                        .param("productName", "mouse")
                        .with(csrf())
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Great product"));
    }

    @Test
    void should_return200_when_findAllByProductNameWithReadScope() throws Exception {
        CommentResponseDto dto = new CommentResponseDto("Great", "Good product review", 5, "john", null);
        Page<CommentResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(commentService.findAllByProductNameByPage(eq("mouse"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/comment/get-comments-product-name")
                        .param("productName", "mouse")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Great"));
    }

    @Test
    void should_return200_when_findAllByAuthorWithReadScope() throws Exception {
        CommentResponseDto dto = new CommentResponseDto("Great", "Good product review", 5, "john", null);
        Page<CommentResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(commentService.findAllByAuthorByPage(eq("john"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/comment/get-comments-author")
                        .param("author", "john")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("john"));
    }

    @Test
    void should_return400ProblemJson_when_createCommentWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/comment/create-comment")
                        .param("productName", "mouse")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
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
}
