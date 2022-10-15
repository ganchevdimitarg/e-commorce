package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.ProductDto;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    ProductDto createProduct(ProductDto productDto, String categoryName);
    Page<ProductDto> getProductsByPage(int page, int size);
    Page<ProductDto> getProductsByCategoryByPage(int page, int size, String categoryName);
    void updateProduct(ProductDto productDto, String productName);
    void deleteProduct(String productName);
}
