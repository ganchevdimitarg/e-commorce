package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.category.CategoryRequestDto;
import com.concordeu.client.catalog.category.CategoryResponseDto;
import org.springframework.data.domain.Page;

public interface CategoryService {
    CategoryResponseDto createCategory(CategoryRequestDto requestDto);

    void deleteCategory(CategoryRequestDto requestDto);

    Page<CategoryResponseDto> getCategoriesByPage(int page, int pageSize);

    void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName);

    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
}
