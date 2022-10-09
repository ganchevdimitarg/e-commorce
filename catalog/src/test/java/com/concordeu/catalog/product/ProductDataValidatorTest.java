package com.concordeu.catalog.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductDataValidatorTest {

    private ProductDataValidator testValidator;

    @BeforeEach
    void setup() {
        testValidator = new ProductDataValidator();
    }

    @Test
    void isValidShouldReturnTrueIfAllDataIsCorrect() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThat(testValidator.validateData(productDto, "PC")).isEqualTo(true);
    }

    @Test
    void isValidShouldThrowExceptionIfProductNameMissing() {
        ProductDto productDto = ProductDto.builder()
                .name("")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfProductNameLengthIsLessThanThree() {
        ProductDto productDto = ProductDto.builder()
                .name("aa")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfProductNameLengthIsGreaterThanTwenty() {
        ProductDto productDto = ProductDto.builder()
                .name("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto,"PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }


    @Test
    void isValidShouldThrowExceptionIfDescriptionMissing() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto,"PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfDescriptionLengthIsLessThanTen() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("aaaaa")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto,"PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfDescriptionLengthIsGreaterThanFifty() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfPriceIsEmpty() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("aaaaaaaaaaaaaaaaaaaaaaaaaa")
                .price(null)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDto, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The price is not correct!");
    }

    @Test
    void isValidShouldThrowExceptionIfSecondParameterIsEmpty() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("aaaaaaaaaaaaaaaaaaaaaaa")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        String secParam = "";

        assertThatThrownBy(() -> testValidator.validateData(productDto, secParam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such name: " + secParam);
    }


}