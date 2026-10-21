package com.ganchevdimitarg.catalog.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.catalog.dto.product.ProductCursor;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Opaque cursor codec for keyset pagination.
 *
 * <p>The token is Base64URL (no padding) over the JSON form of {@link ProductCursor}. The
 * filter fingerprint binds the token to the query that minted it, so replaying a cursor with
 * different filters — or the other sort direction — is a 400 rather than a silently wrong
 * page. Tokens are stateless: no server-side store, no expiry.
 */
@Component
@RequiredArgsConstructor
public class ProductCursorCodec {

    static final String INVALID_CURSOR = "Invalid cursor";
    private static final String SHA_256 = "SHA-256";

    private final ObjectMapper objectMapper;

    /**
     * SHA-256 hex over the canonical {@code q|minPrice|maxPrice|inStock|categoryName|direction}.
     * Null components hash as empty strings, so a blank filter and an absent one agree.
     */
    public String filterHash(ProductSearchCriteria criteria, String direction) {
        String canonical = String.join("|",
                nullSafe(criteria.q()), nullSafe(criteria.minPrice()), nullSafe(criteria.maxPrice()),
                nullSafe(criteria.inStock()), nullSafe(criteria.categoryName()), nullSafe(direction));
        return HexFormat.of().formatHex(sha256().digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public String encode(ProductCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode product cursor", e);
        }
    }

    /**
     * @throws ValidationException when the token is not decodable, is of another version, or
     *                             was minted for different filters or direction
     */
    public ProductCursor decode(String encoded, ProductSearchCriteria criteria, String direction) {
        String requestedDirection = normaliseDirection(direction);
        ProductCursor cursor = parse(encoded);
        if (cursor.version() != ProductCursor.CURRENT_VERSION
                || cursor.createdAt() == null
                || cursor.id() == null
                || !requestedDirection.equals(cursor.direction())
                || !filterHash(criteria, requestedDirection).equals(cursor.filterHash())) {
            throw new ValidationException(INVALID_CURSOR);
        }
        return cursor;
    }

    private ProductCursor parse(String encoded) {
        if (encoded == null) {
            throw new ValidationException(INVALID_CURSOR);
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(json, ProductCursor.class);
        } catch (IllegalArgumentException | IOException e) {
            throw new ValidationException(INVALID_CURSOR);
        }
    }

    private static String normaliseDirection(String direction) {
        if (direction != null) {
            String normalised = direction.trim().toLowerCase(Locale.ROOT);
            if ("asc".equals(normalised) || "desc".equals(normalised)) {
                return normalised;
            }
        }
        throw new ValidationException(INVALID_CURSOR);
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform but is unavailable", e);
        }
    }
}
