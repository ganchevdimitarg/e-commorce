package com.ganchevdimitarg.catalog.dto.product;

import java.util.List;

/**
 * One page of a cursor-paginated product list. {@code nextCursor} is the opaque token for
 * the following page and is {@code null} on the last page.
 */
public record CursorPageResponse<T>(List<T> content, String nextCursor) {
}
