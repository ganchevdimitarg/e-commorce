package com.ganchevdimitarg.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Tag("unit")
class PageableSupportTest {

    @Test
    void should_returnPageable_when_sizeAtMax() {
        Pageable pageable = PageRequest.of(0, 100);
        Pageable result = PageableSupport.capped(pageable);
        assertThat(result).isEqualTo(pageable);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    void should_returnPageable_when_sizeBelowMax() {
        Pageable pageable = PageRequest.of(2, 20);
        Pageable result = PageableSupport.capped(pageable);
        assertThat(result).isEqualTo(pageable);
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    @Test
    void should_throwValidation_when_sizeExceedsMax() {
        Pageable pageable = PageRequest.of(0, 101);
        assertThatThrownBy(() -> PageableSupport.capped(pageable))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }
}
