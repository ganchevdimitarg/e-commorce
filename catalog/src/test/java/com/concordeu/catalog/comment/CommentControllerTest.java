package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.ProductController;
import com.concordeu.catalog.product.ProductService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void createCommentShouldCreateCommentAndReturnIt() throws Exception {
        when(validator.validateData(any())).thenReturn(true);

        mvc.perform(post("/api/v1/comment/create-comment/{productName}", "mouse")
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
                .andExpect(status().isCreated());
    }

    @Test
    void findAllByProductNameShouldReturnAllProductComments() throws Exception {
        mvc.perform(get("/api/v1/comment/get-comments-product-name/{productName}", "productName")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void findAllByAuthorShouldReturnAllAuthorComments() throws Exception {
        mvc.perform(get("/api/v1/comment/get-comments-author/{author}", "author")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getAvgStars() throws Exception {
        mvc.perform(get("/api/v1/comment/get-avg-stars/{productName}", "productName")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}