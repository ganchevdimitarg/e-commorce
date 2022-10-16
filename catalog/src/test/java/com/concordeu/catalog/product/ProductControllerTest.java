package com.concordeu.catalog.product;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.dto.ProductRequestDto;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.catalog.validator.ProductDataValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    MapStructMapper mapper;

    @Test
    void createProductShouldCreateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductDto(any(ProductRequestDto.class))).thenReturn(ProductDto.builder().name("aaaa").description("aaaaaaaaaaaaaaaaa").build());

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
                .andExpect(status().isOk());

        verify(productService).createProduct(any(ProductDto.class),any());
    }

    @Test
    void getProductsByPageShouldGetAllProductsBySearchPage() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<ProductDto> products = Arrays.asList(new ProductDto(), new ProductDto());
        Page<ProductDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByPage(1,5)).willReturn(page);

        mvc.perform(get("/api/v1/product/get-products?page=1&pageSize=5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getProductsByCategoryShouldReturnOnlyProductOneCategory() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<ProductDto> products = Arrays.asList(new ProductDto(), new ProductDto());
        Page<ProductDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByCategoryByPage(1,5, "pc")).willReturn(page);

        mvc.perform(get("/api/v1/product/get-products/{category}?page=1&pageSize=5&categoryName=pc", "pc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void updateProductShouldUpdateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductDto(any(ProductRequestDto.class))).thenReturn(ProductDto.builder().name("aaaa").description("aaaaaaaaaaaaaaaaa").build());

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
                .andExpect(status().isOk());

        verify(productService).updateProduct(any(ProductDto.class),any());
    }

    @Test
    void deleteProductShouldDeleteProduct() throws Exception {
        mvc.perform(delete("/api/v1/product/delete/{productName}","mouse"))
                .andExpect(status().isOk());

        verify(productService).deleteProduct(any());
    }
}