package com.concordeu.catalog.comment;

import com.concordeu.catalog.controller.CommentController;
import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.validator.CommentDataValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = CommentController.class)
@ActiveProfiles("test")
@Tag("integration")
class CommentControllerTest {

    @Autowired
    WebTestClient client;
    @MockBean
    CommentService commentService;
    @MockBean
    CommentDataValidator validator;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createCommentShouldCreateCommentAndReturnIt() throws Exception {
        when(mapper.mapCommentRequestDtoToCommentResponseDto(any(CommentRequestDto.class)))
                .thenReturn(new CommentResponseDto("", "", 0, "", null));
        when(validator.validateData(any())).thenReturn(true);

        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/comment/create-comment/{productName}", "mouse")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "title":  "dsafasfadsa",
                          "text": "i am not very happy",
                          "star":  6.0,
                          "author": "Dimitar"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void findAllByProductNameShouldReturnAllProductComments() throws Exception {
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/comment/get-comments-product-name/{productName}?page=1&size=5", "productName")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void findAllByAuthorShouldReturnAllAuthorComments() throws Exception {
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/comment/get-comments-author/{author}?page=1&size=5", "author")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getAvgStars() throws Exception {
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/comment/get-avg-stars/{productName}", "productName")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }
}