package com.ganchevdimitarg.catalog.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ganchevdimitarg.catalog.dto.product.ProductCursor;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ProductCursorCodecTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-21T10:15:30.123456Z");
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ProductSearchCriteria CRITERIA =
            new ProductSearchCriteria("mouse", new BigDecimal("10"), new BigDecimal("20"), true, "PC");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ProductCursorCodec codec = new ProductCursorCodec(objectMapper);

    @Test
    void should_roundTripCursor_when_encodeThenDecode() {
        ProductCursor cursor = cursor(1, "desc", CRITERIA);

        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded, CRITERIA, "desc")).isEqualTo(cursor);
    }

    @Test
    void should_encodeUrlSafeWithoutPadding_when_encoding() {
        String encoded = codec.encode(cursor(1, "asc", CRITERIA));

        assertThat(encoded)
                .doesNotContain("=")
                .doesNotContain("+")
                .doesNotContain("/");
    }

    @Test
    void should_throwValidation_when_base64IsMalformed() {
        assertThatThrownBy(() -> codec.decode("not base64 !!!", CRITERIA, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_payloadIsNotJson() {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded, CRITERIA, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_payloadIsJsonNull() {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("null".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded, CRITERIA, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_cursorIsNull() {
        assertThatThrownBy(() -> codec.decode(null, CRITERIA, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_versionIsNotOne() {
        String encoded = codec.encode(cursor(2, "desc", CRITERIA));

        assertThatThrownBy(() -> codec.decode(encoded, CRITERIA, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_filterHashDiffersFromRequest() {
        String encoded = codec.encode(cursor(1, "desc", CRITERIA));
        ProductSearchCriteria otherCriteria =
                new ProductSearchCriteria("keyboard", null, null, null, null);

        assertThatThrownBy(() -> codec.decode(encoded, otherCriteria, "desc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_directionDiffersFromRequest() {
        String encoded = codec.encode(cursor(1, "desc", CRITERIA));

        assertThatThrownBy(() -> codec.decode(encoded, CRITERIA, "asc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_throwValidation_when_requestedDirectionIsNotAscOrDesc() {
        String encoded = codec.encode(cursor(1, "desc", CRITERIA));

        assertThatThrownBy(() -> codec.decode(encoded, CRITERIA, "price,asc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void should_hashNullAndBlankCriteriaIdentically_when_computingFilterHash() {
        String nullCriteria = codec.filterHash(
                new ProductSearchCriteria(null, null, null, null, null), "asc");
        String blankCriteria = codec.filterHash(
                new ProductSearchCriteria("   ", null, null, null, "  "), "asc");

        assertThat(nullCriteria).matches("[0-9a-f]{64}");
        assertThat(blankCriteria).isEqualTo(nullCriteria);
    }

    @Test
    void should_changeFilterHash_when_directionChanges() {
        assertThat(codec.filterHash(CRITERIA, "asc"))
                .isNotEqualTo(codec.filterHash(CRITERIA, "desc"));
    }

    private ProductCursor cursor(int version, String direction, ProductSearchCriteria criteria) {
        return new ProductCursor(version, direction, CREATED_AT, PRODUCT_ID,
                codec.filterHash(criteria, direction));
    }
}
