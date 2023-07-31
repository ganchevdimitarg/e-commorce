package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.ItemRequestDTO;
import com.concordeu.catalog.dto.ProductDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productResponseDto, String categoryName);
    Page<ProductDTO> getProductsByPage(int page, int size);
    Page<ProductDTO> getProductsByCategoryByPage(int page, int size, String categoryName);
    ProductDTO getProductByName(String name);
    ProductDTO getProductById(String id);
    void updateProduct(ProductDTO productResponseDto, String productName);
    void deleteProduct(String productName);

    List<ProductDTO> getProductsById(ItemRequestDTO items);
}
