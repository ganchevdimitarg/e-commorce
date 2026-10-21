package com.ganchevdimitarg.catalog.service.product;

import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.CursorPageResponse;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponseDto createProduct(CreateProductCommand command);
    Page<ProductResponseDto> searchProducts(ProductSearchCriteria criteria, Pageable pageable);
    CursorPageResponse<ProductResponseDto> searchProductsByCursor(
            ProductSearchCriteria criteria, String cursor, int limit, String sortDirection);
    ProductResponseDto getProductByName(String name);
    ProductResponseDto getProductById(UUID id);
    /**
     * @param expectedVersion the version from the client's {@code If-Match} ETag, or
     *                        {@code null} for no precondition (absent header or {@code *})
     */
    ProductResponseDto updateProduct(UUID id, UpdateProductCommand command, Long expectedVersion);
    void deleteProduct(UUID id);
    List<ProductResponseDto> getProductsById(ItemRequestDto items);
}
