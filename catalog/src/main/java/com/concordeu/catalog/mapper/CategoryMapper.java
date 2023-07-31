package com.concordeu.catalog.mapper;

import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.entities.Category;
import org.mapstruct.Mapper;

import java.util.Set;

@Mapper
public interface CategoryMapper {
    Category mapCategoryDTOToCategory(CategoryDTO categoryDTO);
    CategoryDTO mapCategoryToCategoryDTO(Category category);
    Set<Category> mapCategoryDTOToCategory(Set<CategoryDTO> categoryDTO);
    Set<CategoryDTO> mapCategoryToCategoryDTO(Set<Category> category);
}
