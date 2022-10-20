package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.product.ProductClient;
import com.concordeu.client.catalog.product.ProductRequestDto;
import com.concordeu.client.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductClient productClient;

    @Override
    public ProductResponseDto createProduct(ProductRequestDto requestDto, String categoryName) {
        Assert.notNull(requestDto, "Request is empty!");
        Assert.notNull(categoryName, "Category name is empty!");
        return productClient.createProduct(requestDto, categoryName);
    }

    @Override
    public Page<ProductResponseDto> getProductsByPage(int page, int pageSize) {
        return productClient.getProducts(page, pageSize);
    }

    @Override
    public Page<ProductResponseDto> getProductsByCategoryByPage(int page, int pageSize, String categoryName) {
        Assert.notNull(categoryName, "Category name is empty!");
        return productClient.getProductsByCategory(page, pageSize, categoryName);
    }

    @Override
    public void updateProduct(ProductRequestDto requestDto, String productName) {
        Assert.notNull(requestDto, "Request is empty!");
        Assert.notNull(productName, "Product name is empty!");
        productClient.updateProduct(requestDto, productName);
    }

    @Override
    public void deleteProduct(String productName) {
        Assert.notNull(productName, "Product name is empty!");
        productClient.deleteProduct(productName);
    }
}
