package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class ServiceAuthorizationTest extends AbstractIntegrationTest {

    @Autowired
    ProductService productService;

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.read")
    void should_denyCreate_when_callerHasOnlyReadScope() {
        CreateProductCommand cmd = new CreateProductCommand(
                "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThatThrownBy(() -> productService.createProduct(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.write")
    void should_allowCreateAttempt_when_callerHasWriteScope() {
        CreateProductCommand cmd = new CreateProductCommand(
                "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        // Will fail later for domain reasons (no category), but NOT with AccessDeniedException
        assertThatThrownBy(() -> productService.createProduct(cmd))
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
