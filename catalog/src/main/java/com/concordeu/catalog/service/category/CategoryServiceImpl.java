package com.concordeu.catalog.service.category;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.CategoryDto;
import com.concordeu.catalog.dao.CategoryRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MapStructMapper mapStructMapper;

    public CategoryDto createCategory(CategoryDto categoryDto) {
        if (categoryDto.getName().isEmpty()) {
            log.error("Category name is empty: " + categoryDto.getName());
            throw new IllegalArgumentException("Category name is empty: " + categoryDto.getName());
        }

        if (categoryRepository.findByName(categoryDto.getName()).isPresent()) {
            log.error("Category with the name: " + categoryDto.getName() + " already exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryDto.getName() + " already exist.");
        }

        Category category = categoryRepository.saveAndFlush(Category.builder().name(categoryDto.getName()).build());

        return mapStructMapper.mapCategoryToDto(category);
    }

    @Override
    public CategoryDto getCategory(String categoryFrom) {
        return mapStructMapper.mapCategoryToDto(categoryRepository.findByName(categoryFrom)
                .orElseThrow(() -> new IllegalArgumentException("No such cateogry: " + categoryFrom)));
    }

    @Override
    public void deleteCategory(CategoryDto categoryDto) {
        checkCategoryData(categoryDto);

        categoryRepository.deleteByName(categoryDto.getName());
    }

    @Override
    public void moveOneProduct(CategoryDto categoryFrom, CategoryDto categoryTo, String productName) {
        checkCategoryData(categoryFrom);
        checkCategoryData(categoryTo);

        Product product = categoryRepository.getById(categoryFrom.getId())
                .getProducts()
                .stream()
                .filter(p -> p.getName().equals(productName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such product: " + productName));

        productRepository.changeCategory(product.getName(), categoryTo.getId());
    }

    @Override
    public void moveAllProducts(CategoryDto categoryFrom, CategoryDto categoryTo) {
        checkCategoryData(categoryFrom);
        checkCategoryData(categoryTo);

        List<Product> category = categoryRepository.getById(categoryFrom.getId()).getProducts();
        for (Product product : category) {
            moveOneProduct(categoryFrom, categoryTo, product.getName());
        }
    }

    @Override
    public List<CategoryDto> getCategories() {
        return mapStructMapper.mapCategoriesToDtos(categoryRepository.findAll());
    }

    private void checkCategoryData(CategoryDto categoryDto) {
        if (categoryDto.getName().isEmpty()) {
            log.error("Category name is empty: " + categoryDto.getName());
            throw new IllegalArgumentException("Category name is empty: " + categoryDto.getName());
        }
        if (categoryRepository.findByName(categoryDto.getName()).isEmpty()) {
            log.error("No such category: " + categoryDto.getName());
            throw new IllegalArgumentException("No such category: " + categoryDto.getName());
        }

    }
}
