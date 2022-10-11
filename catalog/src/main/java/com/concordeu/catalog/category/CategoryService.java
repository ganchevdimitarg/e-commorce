package com.concordeu.catalog.category;

import java.util.List;

public interface CategoryService {
    CategoryDto createCategory(CategoryDto categoryDto);
    CategoryDto getCategory(String categoryFrom);
    void deleteCategory(CategoryDto categoryDto);
    void moveOneProduct(CategoryDto categoryFrom, CategoryDto categoryTo, String productName);
    void moveAllProducts(CategoryDto categoryFrom, CategoryDto categoryTo);
    List<CategoryDto> getCategories();
}
