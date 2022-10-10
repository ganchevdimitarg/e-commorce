package com.concordeu.catalog.category;

import com.concordeu.catalog.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    public CategoryDto createCategory(String categoryName) {
        if (categoryName.isEmpty()) {
            log.error("No such category: " + categoryName);
            throw new IllegalArgumentException("No such category: " + categoryName);
        }

        if (categoryRepository.findByName(categoryName).isPresent()) {
            log.error("Category with the name: " + categoryName + " already exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryName + " already exist.");
        }

        Category category = categoryRepository.saveAndFlush(Category.builder().name(categoryName).build());

        return modelMapper.mapCategoryToDto(category);
    }

    @Override
    public void deleteCategory(String categoryName) {
        if (categoryRepository.findByName(categoryName).isEmpty()){
            log.error("Category with the name: " + categoryName + " does not exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryName + " does not exist.");
        }

        categoryRepository.deleteByName(categoryName);
    }
}
