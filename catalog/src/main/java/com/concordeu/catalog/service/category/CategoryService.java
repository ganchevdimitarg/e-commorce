package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.category.CreateCategoryCommand;
import com.concordeu.catalog.dto.category.MoveProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    CategoryResponseDto createCategory(CreateCategoryCommand command);
    CategoryResponseDto getCategory(String categoryName);
    void deleteCategory(String categoryName);
    void moveOneProduct(MoveProductCommand command);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryResponseDto> getCategoriesByPage(Pageable pageable);
}
