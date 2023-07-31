package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.CategoryDTO;
import org.springframework.data.domain.Page;

public interface CategoryService {
    CategoryDTO createCategory(CategoryDTO categoryDto);
    CategoryDTO getCategory(String categoryFrom);
    void deleteCategory(String categoryName);
    void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryDTO> getCategoriesByPage(int page, int size);
}
