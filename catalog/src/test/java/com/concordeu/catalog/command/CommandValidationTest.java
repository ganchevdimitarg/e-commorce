package com.concordeu.catalog.command;

import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class CommandValidationTest {

    @Test
    void should_buildCommand_when_priceNonNegative() {
        CreateProductCommand cmd =
                new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThat(cmd.name()).isEqualTo("mouse");
    }

    @Test
    void should_throwValidation_when_priceNegative() {
        assertThatThrownBy(() ->
                new CreateProductCommand("mouse", "WiFi mouse USB", new BigDecimal("-1"), true, "", "PC"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price");
    }
}
