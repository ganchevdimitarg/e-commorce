package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.CategoryDto;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CategoryService {
    CategoryDto createCategory(CategoryDto categoryDto);
    CategoryDto getCategory(String categoryFrom);
    void deleteCategory(CategoryDto categoryDto);
    void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryDto> getCategoriesByPage(int page, int size);
}
