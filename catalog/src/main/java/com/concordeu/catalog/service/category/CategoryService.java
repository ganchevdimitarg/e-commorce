package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import org.springframework.data.domain.Page;

public interface CategoryService {
    CategoryResponseDto createCategory(CategoryResponseDto categoryResponseDto);
    CategoryResponseDto getCategory(String categoryFrom);
    void deleteCategory(CategoryResponseDto categoryResponseDto);
    void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryResponseDto> getCategoriesByPage(int page, int size);
}
