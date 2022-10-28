package com.concordeu.catalog.comment;

import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.controller.CommentController;
import com.concordeu.client.catalog.comment.CommentResponseDto;
import com.concordeu.client.catalog.comment.CommentRequestDto;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.validator.CommentDataValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CommentController.class)
@Tag("integration")
class CommentControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    CommentService commentService;
    @MockBean
    CommentDataValidator validator;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createCommentShouldCreateCommentAndReturnIt() throws Exception {
        when(mapper.mapCommentRequestDtoToCommentResponseDto(any(CommentRequestDto.class)))
                .thenReturn(new CommentResponseDto("","",0,"", null));
        when(validator.validateData(any())).thenReturn(true);

        mvc.perform(post("/api/v1/catalog/comment/create-comment/{productName}", "mouse")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title":  "dsafasfadsa",
                          "text": "i am not very happy",
                          "star":  6.0,
                          "author": "Dimitar"
                        }
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void findAllByProductNameShouldReturnAllProductComments() throws Exception {
        mvc.perform(get("/api/v1/catalog/comment/get-comments-product-name/{productName}?page=1&pageSize=5", "productName")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void findAllByAuthorShouldReturnAllAuthorComments() throws Exception {
        mvc.perform(get("/api/v1/catalog/comment/get-comments-author/{author}?page=1&pageSize=5", "author")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getAvgStars() throws Exception {
        mvc.perform(get("/api/v1/catalog/comment/get-avg-stars/{productName}", "productName")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}