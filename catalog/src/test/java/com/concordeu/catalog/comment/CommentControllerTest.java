package com.concordeu.catalog.comment;

import com.concordeu.catalog.controller.CommentController;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = CommentController.class)
@ActiveProfiles("test")
@Tag("integration")
class CommentControllerTest {
/*
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
        when(mapper.mapCommentRequestDtoToCommentResponseDto(any(CommentDTO.class)))
                .thenReturn(new CommentResponseDto("", "", 0, "", null));
        when(validator.validateData(any())).thenReturn(true);

        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/comment/create-comment?productName={productName}", "mouse")
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
                .uri("/api/v1/catalog/comment/get-comments-product-name?productName={productName}&page=1&size=5", "productName")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void findAllByAuthorShouldReturnAllAuthorComments() throws Exception {
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/comment/get-comments-author?author={author}&page=1&size=5", "author")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getAvgStars() throws Exception {
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/comment/get-avg-stars?productName={productName}", "productName")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

 */
}