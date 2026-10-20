package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable predicates for {@code GET /products}. Text search is a case-insensitive
 * contains over the product name; LIKE metacharacters in the user's query are escaped so
 * {@code %} and {@code _} are matched literally.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(ProductSearchCriteria criteria, UUID categoryId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.q() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + escapeLike(criteria.q().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            if (criteria.inStock() != null) {
                predicates.add(cb.equal(root.get("inStock"), criteria.inStock()));
            }
            if (categoryId != null) {
                // The FK lives on products, so no join is needed to filter by category id.
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
