package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryDao categoryDao;
    private final ProductDao productDao;
    private final MapStructMapper mapper;

    public CategoryResponseDto createCategory(CategoryResponseDto categoryResponseDto) {
        if (categoryResponseDto.name().isEmpty()) {
            log.warn("Category name is empty: " + categoryResponseDto.name());
            throw new IllegalArgumentException("Category name is empty: " + categoryResponseDto.name());
        }

        if (categoryDao.findByName(categoryResponseDto.name()).isPresent()) {
            log.warn("Category with the name: " + categoryResponseDto.name() + " already exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryResponseDto.name() + " already exist.");
        }

        Category category = categoryDao.saveAndFlush(Category.builder().name(categoryResponseDto.name()).build());

        return mapper.mapCategoryToCategoryResponseDto(category);
    }

    @Override
    public CategoryResponseDto getCategory(String categoryName) {
        Category category = categoryDao.findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName));
        return convertCategory(category);
    }

    @Override
    public void deleteCategory(String categoryName) {
        if (categoryName.isEmpty()) {
            log.warn("Category name is empty: " + categoryName);
            throw new IllegalArgumentException("Category name is empty: " + categoryName);
        }
        if (categoryDao.findByName(categoryName).isEmpty()) {
            log.warn("No such category: " + categoryName);
            throw new IllegalArgumentException("No such category: " + categoryName);
        }

        categoryDao.deleteByName(categoryName);
    }

    @Override
    public void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName) {
        CategoryResponseDto categoryFrom = getCategory(categoryNameFrom);
        CategoryResponseDto categoryTo = getCategory(categoryNameTo);

        Product product = categoryDao.getById(categoryFrom.id())
                .getProducts()
                .stream()
                .filter(p -> p.getName().equals(productName))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No such product: " + productName);
                    return new IllegalArgumentException("No such product: " + productName);
                });

        productDao.changeCategory(product.getName(), categoryTo.id());
    }

    @Override
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        CategoryResponseDto categoryFrom = getCategory(categoryNameFrom);
        CategoryResponseDto categoryTo = getCategory(categoryNameTo);

        List<Product> category = categoryDao.getById(categoryFrom.id()).getProducts();
        for (Product product : category) {
            moveOneProduct(categoryFrom.name(), categoryTo.name(), product.getName());
        }
    }

    @Override
    public Page<CategoryResponseDto> getCategoriesByPage(int page, int size) {
        Page<CategoryResponseDto> categories = categoryDao
                .findAll(PageRequest.of(page, size))
                .map(this::convertCategory);

        log.info("Successful get categories: " + categories.getSize());

        return categories;
    }

    private CategoryResponseDto convertCategory(Category category) {
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                mapper.mapProductsToProductRequestDtos(category.getProducts()));
    }
}
