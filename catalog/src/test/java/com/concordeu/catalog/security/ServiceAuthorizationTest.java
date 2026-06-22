package com.concordeu.catalog.security;

import com.concordeu.catalog.AbstractIntegrationTest;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.service.product.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class ServiceAuthorizationTest extends AbstractIntegrationTest {

    @Autowired
    ProductService productService;

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.read")
    void should_denyCreate_when_callerHasOnlyReadScope() {
        ProductResponseDto dto = new ProductResponseDto("", "mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        assertThatThrownBy(() -> productService.createProduct(dto, "PC"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.write")
    void should_allowCreateAttempt_when_callerHasWriteScope() {
        ProductResponseDto dto = new ProductResponseDto("", "mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        // Will fail later for domain reasons (no category), but NOT with AccessDeniedException
        assertThatThrownBy(() -> productService.createProduct(dto, "PC"))
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
