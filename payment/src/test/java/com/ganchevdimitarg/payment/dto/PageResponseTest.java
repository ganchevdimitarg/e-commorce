package com.ganchevdimitarg.payment.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@link PageResponse#of(org.springframework.data.domain.Page)} maps every field. */
class PageResponseTest {

    @Test
    void should_mapAllFields_when_pageHasContent() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> result = PageResponse.of(page);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void should_mapEmptyContent_when_pageIsEmpty() {
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> result = PageResponse.of(page);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(0);
    }
}
