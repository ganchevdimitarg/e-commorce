package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
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

    @Autowired
    CommentService commentService;

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.read")
    void should_denyCreate_when_callerHasOnlyReadScope() {
        CreateProductCommand cmd = new CreateProductCommand(
                "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThatThrownBy(() -> productService.createProduct(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_allowCreateAttempt_when_callerHasManageScope() {
        CreateProductCommand cmd = new CreateProductCommand(
                "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        // Will fail later for domain reasons (no category), but NOT with AccessDeniedException
        assertThatThrownBy(() -> productService.createProduct(cmd))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.review")
    void should_denyCreate_when_callerHasOnlyReviewScope() {
        CreateProductCommand cmd = new CreateProductCommand(
                "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThatThrownBy(() -> productService.createProduct(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_denyCommentCreate_when_callerHasOnlyManageScope() {
        CreateCommentCommand cmd = new CreateCommentCommand(
                "nice", "great product!!", 5.0, "joe", "missing-product");
        assertThatThrownBy(() -> commentService.createComment(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.review")
    void should_allowCommentCreateAttempt_when_callerHasReviewScope() {
        CreateCommentCommand cmd = new CreateCommentCommand(
                "nice", "great product!!", 5.0, "joe", "missing-product");
        // The product does not exist, so the service throws NotFoundException — not AccessDenied.
        assertThatThrownBy(() -> commentService.createComment(cmd))
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
