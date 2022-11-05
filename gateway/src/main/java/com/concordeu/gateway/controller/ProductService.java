package com.concordeu.gateway.controller;

import com.concordeu.client.catalog.product.ProductClient;
import com.concordeu.client.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductClient productClient;

    public Page<ProductResponseDto> getProductsByPage(int page, int size) {
        return productClient.getProducts(page, size);
    }

}