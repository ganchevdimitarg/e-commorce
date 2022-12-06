package com.concordeu.catalog.service.product;

import com.concordeu.client.catalog.product.ProductResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponseDto createProduct(ProductResponseDto productResponseDto, String categoryName);
    Page<ProductResponseDto> getProductsByPage(int page, int size);
    Page<ProductResponseDto> getProductsByCategoryByPage(int page, int size, String categoryName);
    ProductResponseDto getProductByName(String name);
    void updateProduct(ProductResponseDto productResponseDto, String productName);
    void deleteProduct(String productName);
}
