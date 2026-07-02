package com.ganchevdimitarg.catalog.dto;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PageResponseTest {

    @Test
    void should_copyPageMetadata_when_builtFromPage() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.of(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
