package com.ganchevdimitarg.catalog.dto.product;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Optional product-search filters. A {@code null} value means "no filter"; blank text filters
 * are normalised to {@code null} so an empty query string behaves like an absent parameter.
 * The compact constructor repeats the bean-validation rules so direct callers (for example
 * the service tests) get the same contract as the controller.
 */
public record ProductSearchCriteria(
        @Size(max = 200) String q,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        Boolean inStock,
        @Size(max = 200) String categoryName) {

    public ProductSearchCriteria {
        q = trimToNull(q);
        categoryName = trimToNull(categoryName);
        if (q != null && q.length() > 200)
            throw new ValidationException("q must not exceed 200 characters");
        if (categoryName != null && categoryName.length() > 200)
            throw new ValidationException("categoryName must not exceed 200 characters");
        if (minPrice != null && minPrice.signum() < 0)
            throw new ValidationException("minPrice must not be negative");
        if (maxPrice != null && maxPrice.signum() < 0)
            throw new ValidationException("maxPrice must not be negative");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new ValidationException("minPrice must not exceed maxPrice");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
