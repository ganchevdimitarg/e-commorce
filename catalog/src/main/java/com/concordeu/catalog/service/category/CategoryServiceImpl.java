package com.concordeu.catalog.service.category;

import com.concordeu.catalog.mapper.CategoryMapper;
import com.concordeu.catalog.repositories.CategoryRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper mapper;

    @Override
    public CategoryDTO createCategory(CategoryDTO categoryDTO) {

        if (categoryRepository.findByName(categoryDTO.name()).isPresent()) {
            log.warn("Category with the name: " + categoryDTO.name() + " already exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryDTO.name() + " already exist.");
        }

        Category category = categoryRepository.saveAndFlush(mapper.mapCategoryDTOToCategory(categoryDTO));

        return mapper.mapCategoryToCategoryDTO(category);
    }

    @Override
    @Cacheable(value="Category", key="#categoryId")
    public CategoryDTO getCategory(String categoryName) {
        Category category = categoryRepository.findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName));
        return convertCategory(category);
    }
    @Transactional
    @Override
    @CacheEvict(value="Category", key="#categoryId")
    public void deleteCategory(String categoryName) {
        if (categoryName.isEmpty()) {
            log.warn("Category name is empty: " + categoryName);
            throw new IllegalArgumentException("Category name is empty: " + categoryName);
        }
        if (categoryRepository.findByName(categoryName).isEmpty()) {
            log.warn("No such category: " + categoryName);
            throw new IllegalArgumentException("No such category: " + categoryName);
        }

        categoryRepository.deleteByName(categoryName);
    }

    @Override
    public void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName) {
        CategoryDTO categoryFrom = getCategory(categoryNameFrom);
        CategoryDTO categoryTo = getCategory(categoryNameTo);

        Product product = categoryRepository.getById(categoryFrom.id())
                .getProducts()
                .stream()
                .filter(p -> p.getName().equals(productName))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No such product: " + productName);
                    return new IllegalArgumentException("No such product: " + productName);
                });

        productRepository.changeCategory(product.getName(), categoryTo.id());
    }

    @Transactional
    @Override
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        CategoryDTO categoryFrom = getCategory(categoryNameFrom);
        CategoryDTO categoryTo = getCategory(categoryNameTo);

        Set<Product> category = categoryRepository.getById(categoryFrom.id()).getProducts();
        for (Product product : category) {
            moveOneProduct(categoryFrom.name(), categoryTo.name(), product.getName());
        }
    }

    @Override
    @Cacheable(value="Category", key="#categoryId")
    public Page<CategoryDTO> getCategoriesByPage(int page, int size) {
        Page<CategoryDTO> categories = categoryRepository
                .findAll(PageRequest.of(page, size))
                .map(this::convertCategory);

        log.info("Successful get categories: " + categories.getSize());

        return categories;
    }

    private CategoryDTO convertCategory(Category category) {
        return mapper.mapCategoryToCategoryDTO(category);
    }
}
