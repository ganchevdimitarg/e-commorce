package com.ganchevdimitarg.catalog.contract;

import com.ganchevdimitarg.catalog.controller.ProductController;
import com.ganchevdimitarg.catalog.dto.category.CategorySummaryDto;
import com.ganchevdimitarg.catalog.dto.product.CursorPageResponse;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.mapper.MapStructMapperImpl;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Standalone MockMvc wiring for the Spring Cloud Contract producer tests generated from
 * {@code src/test/resources/contracts/catalog}. The real {@link ProductController} and
 * {@link ControllerExceptionHandler} run; only {@link ProductService} is mocked, so the
 * contracts assert the controller's own status codes, headers and JSON shape without a
 * Spring context or Testcontainers.
 *
 * <p>The mapper is instantiated directly instead of through {@code Mappers.getMapper}:
 * MapStruct's Spring component model generates no {@code META-INF/services} entry, so the
 * {@code MapStructMapperImpl} that the main compile produces is the only usable instance.
 * The pageable resolver is registered explicitly because {@code StandaloneMockMvcBuilder}
 * does not install Spring Data's argument resolvers, and the list contract binds a
 * {@code Pageable}.
 */
public abstract class ContractVerifierBase {

    protected static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    protected static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    protected static final String NEXT_CURSOR = "next-cursor-token";

    protected static final String PRODUCT_NAME = "mouse";
    protected static final String PRODUCT_DESCRIPTION = "WiFi mouse USB";
    protected static final String CATEGORY_NAME = "PC";

    private final MapStructMapper mapper = new MapStructMapperImpl();

    @BeforeEach
    void setUpContractVerifier() {
        ProductService productService = mock(ProductService.class);
        when(productService.getProductById(PRODUCT_ID)).thenReturn(product(3L, 4.5, 2));
        when(productService.searchProducts(any(ProductSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product(3L, 4.5, 2)), PageRequest.of(0, 20), 1));
        when(productService.createProduct(any())).thenReturn(product(0L, 4.5, 2));
        when(productService.updateProduct(eq(PRODUCT_ID), any(), eq(3L)))
                .thenReturn(updatedProduct());
        when(productService.getProductsById(any(ItemRequestDto.class)))
                .thenReturn(List.of(product(3L, 4.5, 2)));
        when(productService.searchProductsByCursor(any(ProductSearchCriteria.class), isNull(), eq(20), eq("desc")))
                .thenReturn(new CursorPageResponse<>(List.of(product(3L, 4.5, 2)), NEXT_CURSOR));

        RestAssuredMockMvc.standaloneSetup(MockMvcBuilders
                .standaloneSetup(new ProductController(productService, mapper))
                .setControllerAdvice(new ControllerExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()));
    }

    private static ProductResponseDto product(long version, double avgStars, int ratingCount) {
        return new ProductResponseDto(
                PRODUCT_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, new BigDecimal("29.99"), true, "black",
                new CategorySummaryDto(CATEGORY_ID, CATEGORY_NAME), List.of(), version, avgStars, ratingCount);
    }

    private static ProductResponseDto updatedProduct() {
        return new ProductResponseDto(
                PRODUCT_ID, PRODUCT_NAME, "WiFi mouse USB v2", new BigDecimal("19.99"), false, "black",
                new CategorySummaryDto(CATEGORY_ID, CATEGORY_NAME), List.of(), 4L, 4.5, 2);
    }
}
