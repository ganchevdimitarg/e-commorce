package com.ganchevdimitarg.catalog.service.product;

import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
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
    ProductResponseDto getProductByName(String name);
    ProductResponseDto getProductById(UUID id);
    ProductResponseDto updateProduct(UUID id, UpdateProductCommand command);
    void deleteProduct(UUID id);
    List<ProductResponseDto> getProductsById(ItemRequestDto items);
}
