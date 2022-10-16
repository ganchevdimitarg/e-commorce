package com.concordeu.catalog.service.category;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.CategoryDto;
import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductDao;
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
    private final MapStructMapper mapStructMapper;

    public CategoryDto createCategory(CategoryDto categoryDto) {
        if (categoryDto.getName().isEmpty()) {
            log.error("Category name is empty: " + categoryDto.getName());
            throw new IllegalArgumentException("Category name is empty: " + categoryDto.getName());
        }

        if (categoryDao.findByName(categoryDto.getName()).isPresent()) {
            log.error("Category with the name: " + categoryDto.getName() + " already exist.");
            throw new IllegalArgumentException("Category with the name: " + categoryDto.getName() + " already exist.");
        }

        Category category = categoryDao.saveAndFlush(Category.builder().name(categoryDto.getName()).build());

        return mapStructMapper.mapCategoryToDto(category);
    }

    @Override
    public CategoryDto getCategory(String categoryName) {
        return mapStructMapper.mapCategoryToDto(categoryDao.findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName)));
    }

    @Override
    public void deleteCategory(CategoryDto categoryDto) {
        if (categoryDto.getName().isEmpty()) {
            log.error("Category name is empty: " + categoryDto.getName());
            throw new IllegalArgumentException("Category name is empty: " + categoryDto.getName());
        }
        if (categoryDao.findByName(categoryDto.getName()).isEmpty()) {
            log.error("No such category: " + categoryDto.getName());
            throw new IllegalArgumentException("No such category: " + categoryDto.getName());
        }

        categoryDao.deleteByName(categoryDto.getName());
    }

    @Override
    public void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName) {
        CategoryDto categoryFrom = getCategory(categoryNameFrom);
        CategoryDto categoryTo = getCategory(categoryNameTo);

        Product product = categoryDao.getById(categoryFrom.getId())
                .getProducts()
                .stream()
                .filter(p -> p.getName().equals(productName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such product: " + productName));

        productDao.changeCategory(product.getName(), categoryTo.getId());
    }

    @Override
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        CategoryDto categoryFrom = getCategory(categoryNameFrom);
        CategoryDto categoryTo = getCategory(categoryNameTo);

        List<Product> category = categoryDao.getById(categoryFrom.getId()).getProducts();
        for (Product product : category) {
            moveOneProduct(categoryFrom.getName(), categoryTo.getName(), product.getName());
        }
    }

    @Override
    public Page<CategoryDto> getCategoriesByPage(int page, int size) {
        Page<CategoryDto> categories = categoryDao
                .findAll(PageRequest.of(page, size))
                .map(CategoryDto::convertCategory);
        log.info("Successful get categories: " + categories.getSize());

        return categories;
    }
}
