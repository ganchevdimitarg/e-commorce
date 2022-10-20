package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.product.ProductRequestDto;
import com.concordeu.client.catalog.product.ProductResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponseDto createProduct(ProductRequestDto requestDto, String categoryName);

    Page<ProductResponseDto> getProductsByPage(int page, int pageSize);

    Page<ProductResponseDto> getProductsByCategoryByPage(int page, int pageSize, String categoryName);

    void updateProduct(ProductRequestDto requestDto, String productName);

    void deleteProduct(String productName);

    ProductResponseDto getProductsByName(String productName);
}
