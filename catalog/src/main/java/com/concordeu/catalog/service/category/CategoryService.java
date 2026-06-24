package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    CategoryResponseDto createCategory(CategoryResponseDto categoryResponseDto);
    CategoryResponseDto getCategory(String categoryFrom);
    void deleteCategory(String categoryName);
    void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryResponseDto> getCategoriesByPage(Pageable pageable);
}
