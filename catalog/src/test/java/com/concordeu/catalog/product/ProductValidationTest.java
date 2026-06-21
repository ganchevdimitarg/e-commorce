package com.concordeu.catalog.product;

import com.concordeu.catalog.config.ResourceServerConfig;
import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.excaption.ControllerExceptionHandler;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = ProductController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class})
class ProductValidationTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ProductService productService;
    @MockitoBean
    MapStructMapper mapper;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void should_return400ProblemJson_when_createProductBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/product/create-product")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "a",
                                    "description": "short",
                                    "price": null,
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(productService);
    }
}
