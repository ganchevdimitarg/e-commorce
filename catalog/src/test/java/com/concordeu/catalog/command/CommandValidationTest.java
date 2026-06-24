package com.concordeu.catalog.command;

import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import com.concordeu.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class CommandValidationTest {

    // --- CreateProductCommand ---

    @Test
    void should_buildCreateCommand_when_priceNonNegative() {
        CreateProductCommand cmd =
                new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThat(cmd.name()).isEqualTo("mouse");
    }

    @Test
    void should_throwValidation_when_createPriceNegative() {
        assertThatThrownBy(() ->
                new CreateProductCommand("mouse", "WiFi mouse USB", new BigDecimal("-1"), true, "", "PC"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price");
    }

    @Test
    void should_buildCreateCommand_when_priceZero() {
        assertThatNoException().isThrownBy(() ->
                new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ZERO, true, "", "PC"));
    }

    // --- UpdateProductCommand ---

    @Test
    void should_buildUpdateCommand_when_priceNonNegative() {
        UpdateProductCommand cmd =
                new UpdateProductCommand("valid description text", BigDecimal.TEN, true, "black");
        assertThat(cmd.price()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void should_throwValidation_when_updatePriceNegative() {
        assertThatThrownBy(() ->
                new UpdateProductCommand("valid description text", new BigDecimal("-5"), false, ""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price");
    }

    @Test
    void should_buildUpdateCommand_when_priceZero() {
        assertThatNoException().isThrownBy(() ->
                new UpdateProductCommand("valid description text", BigDecimal.ZERO, true, ""));
    }
}
