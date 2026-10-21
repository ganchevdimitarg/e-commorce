package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.ProductCursor;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;

import java.util.List;
import java.util.UUID;

/**
 * Keyset page read for product search. Deliberately not a Spring Data {@code Page} query:
 * cursor pagination needs no total count, and {@code findAll(Specification, Pageable)} would
 * issue one on every page.
 */
public interface ProductCursorRepository {

    /**
     * @param criteria    filters to apply; the specification is reused from offset search
     * @param categoryId  resolved category id, or {@code null} for no category filter
     * @param position    the last row of the previous page, or {@code null} for the first page
     * @param direction   {@code asc} or {@code desc} on {@code (createdAt, id)}
     * @param limit       maximum rows to return; the caller asks for {@code pageSize + 1}
     */
    List<Product> findPage(ProductSearchCriteria criteria, UUID categoryId,
                           ProductCursor position, String direction, int limit);
}
