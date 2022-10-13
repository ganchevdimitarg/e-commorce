package com.concordeu.catalog.product;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.dto.ProductRequestDto;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.catalog.validator.ProductDataValidator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ProductController.class)
@Tag("integration")
class ProductControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    ProductService productService;
    @MockBean
    ProductDataValidator validator;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createProductShouldCreateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductDto(any(ProductRequestDto.class))).thenReturn(ProductDto.builder().name("aaaa").description("aaaaaaaaaaaaaaaaa").build());
        when(validator.validateData(any(), any())).thenReturn(true);

        mvc.perform(post("/api/v1/product/create-product/{categoryName}","PC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "aaaa",
                                    "description": "aaaaaaaaaaaaaaaaa",
                                    "price": "140",
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(productService).createProduct(any(ProductDto.class),any());
    }

    @Test
    void getProductsShouldGetAllProducts() throws Exception {
        when(productService.getProducts()).thenReturn(List.of(ProductDto.builder().name("mouse").build()));

        mvc.perform(get("/api/v1/product/get-products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("mouse"))
                .andExpect(jsonPath("$.size()", Matchers.is(1)));
    }

    @Test
    void getProductsByCategoryShouldReturnOnlyProductOneCategory() throws Exception {
        when(productService.getProductsByCategory("PC")).thenReturn(List.of(
                ProductDto.builder().name("mouse").build(),
                ProductDto.builder().name("keyboard").build()));

        mvc.perform(get("/api/v1/product/get-products/{categoryName}", "PC")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("mouse"))
                .andExpect(jsonPath("$[1].name").value("keyboard"))
                .andExpect(jsonPath("$.size()", Matchers.is(2)));
    }

    @Test
    void updateProductShouldUpdateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductDto(any(ProductRequestDto.class))).thenReturn(ProductDto.builder().name("aaaa").description("aaaaaaaaaaaaaaaaa").build());
        when(validator.validateData(any(), any())).thenReturn(true);

        mvc.perform(put("/api/v1/product/update-product/{productName}", "mouse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "aaaa",
                                    "description": "golden retrivar",
                                    "price": "140",
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(productService).updateProduct(any(ProductDto.class),any());
    }

    @Test
    void deleteProductShouldDeleteProduct() throws Exception {
        mvc.perform(delete("/api/v1/product/delete/{productName}","mouse"))
                .andExpect(status().isAccepted());

        verify(productService).deleteProduct(any());
    }
}