package com.concordeu.catalog.product;

import java.util.List;

public interface ProductService {

    ProductDto createProduct(ProductDto productDto, String category);

    List<ProductDto> getProducts();
    List<ProductDto> getProductsByCategory(String category);

    void updateProduct(String productName, ProductDto productDto);

    void deleteProduct(String productName);
}
