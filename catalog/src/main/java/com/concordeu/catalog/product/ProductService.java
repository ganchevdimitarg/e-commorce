package com.concordeu.catalog.product;

import java.util.List;

public interface ProductService {

    ProductDto createProduct(ProductDto productDto, String category);

    List<ProductDto> getProducts();
    List<ProductDto> getProductsByCategory(String category);

    void updateProduct(ProductDto productDto, String productName);

    void deleteProduct(String productName);
}
