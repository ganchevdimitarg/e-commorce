package com.concordeu.catalog.product;

import com.concordeu.catalog.CatalogApplication;
import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

import org.springframework.security.test.context.support.WithMockUser;

@WebFluxTest(
        controllers = {
                ProductController.class
        })
@ActiveProfiles("test")
@Tag("integration")
class ProductControllerTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;
    @MockBean
    ProductService productService;
    @MockBean
    MapStructMapper mapper;
    ProductResponseDto productResponseDto;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient
                .bindToApplicationContext(this.context)
                .apply(springSecurity())
                .configureClient()
                .filter(basicAuthentication("admin", "admin"))
                .build();
        productResponseDto = new ProductResponseDto("", "aaaa", "aaaaaaaaaaaaaaaaa", BigDecimal.ONE,
                false, "", null, new ArrayList<>());
    }

    @Test
    void createProductShouldCreateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductResponseDto(any(ProductRequestDto.class))).thenReturn(productResponseDto);

        this.client.mutateWith(mockUser())
                .post()
                .uri("/api/v1/catalog/products/create-product/{categoryName}", "PC")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody()
                .isEmpty();

        verify(productService).createProduct(any(ProductResponseDto.class), any());
    }

   /* @Test
    @WithMockUser(username = "admin", password = "admin")
    void getProductsByPageShouldGetAllProductsBySearchPage() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);

        List<ProductResponseDto> products = Arrays.asList(productResponseDto, productResponseDto);
        Page<ProductResponseDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByPage(0, 5)).willReturn(page);

        mvc.perform(get("/api/v1/catalog/products/get-products?page=1&size=5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", password = "admin")
    void getProductsByCategoryShouldReturnOnlyProductOneCategory() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<ProductResponseDto> products = Arrays.asList(productResponseDto, productResponseDto);
        Page<ProductResponseDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByCategoryByPage(1, 5, "pc")).willReturn(page);

        mvc.perform(get("/api/v1/catalog/products/get-products/{category}?page=1&size=5", "pc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", password = "admin")
    void updateProductShouldUpdateProduct() throws Exception {
        when(mapper.mapProductRequestDtoToProductResponseDto(any(ProductRequestDto.class))).thenReturn(productResponseDto);

        mvc.perform(put("/api/v1/catalog/products/update-product/{productName}", "mouse")
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

        verify(productService).updateProduct(any(ProductResponseDto.class), any());
    }

    @Test
    @WithMockUser(username = "admin", password = "admin")
    void deleteProductShouldDeleteProduct() throws Exception {
        mvc.perform(delete("/api/v1/catalog/products/delete-product/{productName}", "mouse"))
                .andExpect(status().isOk());

        verify(productService).deleteProduct(any());
    }*/
}