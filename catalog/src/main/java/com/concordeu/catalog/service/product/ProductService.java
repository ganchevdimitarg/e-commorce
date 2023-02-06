package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    ProductResponseDto createProduct(ProductResponseDto productResponseDto, String categoryName);
    Page<ProductResponseDto> getProductsByPage(int page, int size);
    Page<ProductResponseDto> getProductsByCategoryByPage(int page, int size, String categoryName);
    ProductResponseDto getProductByName(String name);
    ProductResponseDto getProductById(String id);
    void updateProduct(ProductResponseDto productResponseDto, String productName);
    void deleteProduct(String productName);

    List<ProductResponseDto> getProductsById(ItemRequestDto items);
}
