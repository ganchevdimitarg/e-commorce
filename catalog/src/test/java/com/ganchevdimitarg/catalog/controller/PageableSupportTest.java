package com.ganchevdimitarg.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

@Tag("unit")
class PageableSupportTest {

    @Test
    void should_returnPageable_when_sizeAtMax() {
        Pageable pageable = PageRequest.of(0, 100);
        Pageable result = PageableSupport.capped(pageable, Set.of("name"));
        assertThat(result).isEqualTo(pageable);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    void should_returnPageable_when_sizeBelowMax() {
        Pageable pageable = PageRequest.of(2, 20);
        Pageable result = PageableSupport.capped(pageable, Set.of("name"));
        assertThat(result).isEqualTo(pageable);
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    @Test
    void should_throwValidation_when_sizeExceedsMax() {
        Pageable pageable = PageRequest.of(0, 101);
        assertThatThrownBy(() -> PageableSupport.capped(pageable, Set.of("name")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void should_rejectUnknownSortProperty_when_notWhitelisted() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("doesNotExist"));

        assertThatThrownBy(() -> PageableSupport.capped(pageable, Set.of("name", "price")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("doesNotExist");
    }

    @Test
    void should_acceptWhitelistedSortProperty_when_exactMatch() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "name"));

        assertThat(PageableSupport.capped(pageable, Set.of("name", "price"))).isEqualTo(pageable);
    }

    @Test
    void should_rejectCaseMismatchedSortProperty_when_propertyIsNotExact() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("Name"));

        assertThatThrownBy(() -> PageableSupport.capped(pageable, Set.of("name")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void should_rejectUnknownSortProperty_when_oneOfSeveralOrdersIsInvalid() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").and(Sort.by("hax")));

        assertThatThrownBy(() -> PageableSupport.capped(pageable, Set.of("name")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("hax");
    }
}
