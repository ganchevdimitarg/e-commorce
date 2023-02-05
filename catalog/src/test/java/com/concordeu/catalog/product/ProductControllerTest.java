package com.concordeu.catalog.product;

import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;


@WebFluxTest(controllers = ProductController.class)
@ActiveProfiles("test")
@Tag("integration")
class ProductControllerTest {
/*
    @Autowired
    WebTestClient client;
    @MockBean
    ProductService productService;
    @MockBean
    MapStructMapper mapper;
    ProductResponseDto productResponseDto;

    @BeforeEach
    void setUp() {
        productResponseDto = new ProductResponseDto("", "aaaa", "aaaaaaaaaaaaaaaaa", BigDecimal.ONE,
                false, "", null, new ArrayList<>());
    }

    @Test
    void createProductShouldCreateProduct() {
        when(mapper.mapProductRequestDtoToProductResponseDto(any(ProductRequestDto.class))).thenReturn(productResponseDto);

        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/product/create-product?categoryName={categoryName}", "PC")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "name": "aaaa",
                            "description": "aaaaaaaaaaaaaaaaa",
                            "price": "140",
                            "inStock": true,
                            "characteristics": "black"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        System.out.println();
    }

    @Test
    void getProductsByPageShouldGetAllProductsBySearchPage() {
        PageRequest pageRequest = PageRequest.of(1, 5);

        List<ProductResponseDto> products = Arrays.asList(productResponseDto, productResponseDto);
        Page<ProductResponseDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByPage(0, 5)).willReturn(page);

        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/product/get-products?page=1&size=5")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProductsByCategoryShouldReturnOnlyProductOneCategory() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<ProductResponseDto> products = Arrays.asList(productResponseDto, productResponseDto);
        Page<ProductResponseDto> page = new PageImpl<>(products, pageRequest, products.size());

        given(productService.getProductsByCategoryByPage(1, 5, "pc")).willReturn(page);

        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/catalog/product/get-products?categoryName={category}&page=1&size=5", "pc")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void updateProductShouldUpdateProduct() {
        when(mapper.mapProductRequestDtoToProductResponseDto(any(ProductRequestDto.class))).thenReturn(productResponseDto);

        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .put()
                .uri("/api/v1/catalog/product/update-product?productName={productName}", "mouse")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "name": "aaaa",
                            "description": "golden retrivar",
                            "price": "140",
                            "inStock": true,
                            "characteristics": "black"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

    }

    @Test
    void deleteProductShouldDeleteProduct() throws Exception {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .delete()
                .uri("/api/v1/catalog/product/delete-product?productName={productName}", "mouse")
                .exchange()
                .expectStatus().isOk();

    }

 */
}
