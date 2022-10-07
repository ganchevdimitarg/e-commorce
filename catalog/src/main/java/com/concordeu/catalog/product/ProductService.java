package com.concordeu.catalog.product;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productDTO, String category);

    List<ProductDTO> getProducts();
    List<ProductDTO> getProductsByCategory(String category);

    void updateProduct(String productName, ProductDTO productDTO);

    ProductDTO deleteProduct(String productName);
}
