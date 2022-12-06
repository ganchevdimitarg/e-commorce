package com.concordeu.catalog.product;

import com.concordeu.catalog.validator.ProductDataValidator;
import com.concordeu.client.catalog.product.ProductResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("unit")
class ProductDataValidatorTest {

    private ProductDataValidator testValidator;

    @BeforeEach
    void setup() {
        testValidator = new ProductDataValidator();
    }

    @Test
    void validateDataShouldReturnTrueIfAllDataIsCorrect() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "WiFi mouse",
                BigDecimal.ONE, true, "", null, new ArrayList<>());

        assertThat(testValidator.validateData(productResponseDto, "PC")).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameMissing() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "", "WiFi mouse",
                BigDecimal.ONE, true, "", null, new ArrayList<>());

        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameLengthIsLessThanThree() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "aa", "WiFi mouse",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameLengthIsGreaterThanTwenty() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "WiFi mouse",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionMissing() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "",
                BigDecimal.ONE, true, "", null, new ArrayList<>());

        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionLengthIsLessThanTen() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "aaaaa",
                BigDecimal.ONE, true, "", null, new ArrayList<>());

        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionLengthIsGreaterThanFifty() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfPriceIsEmpty() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "aaaaaaaaaaaaaaaaaaaaaaaaaa",
                null, true, "", null, new ArrayList<>());

        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The price is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfSecondParameterIsEmpty() {
        ProductResponseDto productResponseDto = new ProductResponseDto("", "mouse", "aaaaaaaaaaaaaaaaaaaaaaa",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
        String secParam = "";

        assertThatThrownBy(() -> testValidator.validateData(productResponseDto, secParam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such name: " + secParam);
    }

}