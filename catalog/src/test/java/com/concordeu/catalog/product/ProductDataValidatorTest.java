package com.concordeu.catalog.product;

import com.concordeu.catalog.dto.ProductDTO;
import com.concordeu.catalog.validator.ProductDataValidator;
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
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .description("testtesttest")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThat(testValidator.validateData(productDTO, "test2")).isTrue();
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameMissing() {
        ProductDTO productDTO = ProductDTO.builder()
                .description("testtest")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "test2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameLengthIsLessThanThree() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("te")
                .description("testtest")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "test2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfProductNameLengthIsGreaterThanTwenty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .description("testtest")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The name is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionMissing() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionLengthIsLessThanTen() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .description("test")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfDescriptionLengthIsGreaterThanFifty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .description("testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttest")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The description is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfPriceIsEmpty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .description("aaaaaaaaaaaaaaaaaaaaaaaaaa")
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testValidator.validateData(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The price is not correct!");
    }

    @Test
    void validateDataShouldThrowExceptionIfSecondParameterIsEmpty() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("test1")
                .description("aaaaaaaaaaaaaaaaaaaaaaa")
                .price(BigDecimal.ONE)
                .inStock(true)
                .build();
        String secParam = "";

        assertThatThrownBy(() -> testValidator.validateData(productDTO, secParam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such name: " + secParam);
    }

}