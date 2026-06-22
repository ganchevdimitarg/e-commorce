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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
