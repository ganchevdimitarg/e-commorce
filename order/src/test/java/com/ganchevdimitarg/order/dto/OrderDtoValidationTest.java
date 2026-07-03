package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.Item;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDtoValidationTest {

    private static Validator validator() {
        try (ValidatorFactory f = Validation.buildDefaultValidatorFactory()) {
            return f.getValidator();
        }
    }

    @Test
    void should_reportViolation_when_usernameBlank() {
        OrderDto dto = OrderDto.builder().username(" ")
                .items(List.of(Item.builder().productId("p_1").quantity(1).build())).build();
        assertThat(validator().validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void should_reportViolation_when_itemsEmpty() {
        OrderDto dto = OrderDto.builder().username("john").items(List.of()).build();
        assertThat(validator().validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("items"));
    }

    @Test
    void should_pass_when_valid() {
        OrderDto dto = OrderDto.builder().username("john")
                .items(List.of(Item.builder().productId("p_1").quantity(1).build())).build();
        assertThat(validator().validate(dto)).isEmpty();
    }
}
