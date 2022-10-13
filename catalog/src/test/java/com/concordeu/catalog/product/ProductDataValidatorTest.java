package com.concordeu.catalog.product;

import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.validator.ProductDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThat(testValidator.validateData(productDto, "PC")).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameMissing() {
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
    void validateDataShouldThrowExceptionIfProductNameLengthIsLessThanThree() {
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
    void validateDataShouldThrowExceptionIfProductNameLengthIsGreaterThanTwenty() {
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
    void validateDataShouldThrowExceptionIfDescriptionMissing() {
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
    void validateDataShouldThrowExceptionIfDescriptionLengthIsLessThanTen() {
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
    void validateDataShouldThrowExceptionIfDescriptionLengthIsGreaterThanFifty() {
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
    void validateDataShouldThrowExceptionIfPriceIsEmpty() {
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
    void validateDataShouldThrowExceptionIfSecondParameterIsEmpty() {
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