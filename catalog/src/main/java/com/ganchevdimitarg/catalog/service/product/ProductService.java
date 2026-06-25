package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponseDto createProduct(CreateProductCommand command);
    Page<ProductResponseDto> getProductsByPage(Pageable pageable);
    Page<ProductResponseDto> getProductsByCategoryByPage(Pageable pageable, String categoryName);
    ProductResponseDto getProductByName(String name);
    ProductResponseDto getProductById(UUID id);
    void updateProduct(UUID id, UpdateProductCommand command);
    void deleteProduct(UUID id);
    List<ProductResponseDto> getProductsById(ItemRequestDto items);
}
