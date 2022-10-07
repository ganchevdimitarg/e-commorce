package com.concordeu.catalog.product;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productDTO, String category);

    List<ProductDTO> getProducts(String category);

    ProductDTO updateProduct(String productName);

    ProductDTO deleteProduct(String productName);
}
