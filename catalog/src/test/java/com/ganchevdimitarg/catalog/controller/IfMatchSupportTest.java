package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class IfMatchSupportTest {

    @Test
    void should_returnNull_when_headerIsAbsent() {
        assertThat(IfMatchSupport.expectedVersion(null)).isNull();
    }

    @Test
    void should_returnNull_when_headerIsBlank() {
        assertThat(IfMatchSupport.expectedVersion("   ")).isNull();
    }

    @Test
    void should_returnNull_when_headerIsWildcard() {
        assertThat(IfMatchSupport.expectedVersion("*")).isNull();
    }

    @Test
    void should_returnVersion_when_headerIsStrongTag() {
        assertThat(IfMatchSupport.expectedVersion("\"3\"")).isEqualTo(3L);
    }

    @Test
    void should_throwValidation_when_headerIsWeakTag() {
        assertThatThrownBy(() -> IfMatchSupport.expectedVersion("W/\"3\""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("strong ETag");
    }

    @Test
    void should_throwValidation_when_headerIsNotNumeric() {
        assertThatThrownBy(() -> IfMatchSupport.expectedVersion("\"x\""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Malformed If-Match ETag");
    }

    @Test
    void should_throwValidation_when_headerHasMultipleTags() {
        assertThatThrownBy(() -> IfMatchSupport.expectedVersion("\"1\", \"2\""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Malformed If-Match ETag");
    }
}
