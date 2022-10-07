package com.concordeu.catalog.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ProductDataValidatorTest {

    private ProductDataValidator testValidator;

    @BeforeEach
    void setup() {
        testValidator = new ProductDataValidator();
    }

    @Test
    void isValidShouldReturnTrueIfAllDataIsCorrect() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThat(testValidator.isValid(productDTO)).isEqualTo(true);
    }

    @Test
    void isValidShouldThrowExceptionIfNameMissing() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfNameLengthIsLessThanThree() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("aa")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfNameLengthIsGreaterThanTwenty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfDescriptionMissing() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfNameLengthIsLessThanTen() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("aaaaa")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfNameLengthIsGreaterThanFifty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.isValid(productDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }
}