package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.category.CategoryClient;
import com.concordeu.client.catalog.category.CategoryRequestDto;
import com.concordeu.client.catalog.category.CategoryResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService{

    private final CategoryClient categoryClient;


    @Override
    public CategoryResponseDto createCategory(CategoryRequestDto requestDto) {
        Assert.notNull(requestDto, "Request is empty");

        return categoryClient.createCategory(requestDto);
    }

    @Override
    public void deleteCategory(CategoryRequestDto requestDto) {
        Assert.notNull(requestDto, "Request is empty");
        categoryClient.deleteCategory(requestDto);
    }

    @Override
    public Page<CategoryResponseDto> getCategoriesByPage(int page, int pageSize) {
        return categoryClient.getCategories(page,pageSize);
    }

    @Override
    public void moveOneProduct(String categoryNameFrom, String categoryNameTo, String productName) {
        Assert.notNull(categoryNameFrom, "Category name from is empty");
        Assert.notNull(categoryNameTo, "Category name to is empty");
        Assert.notNull(productName, "Product name to is empty");
        categoryClient.moveOneProduct(categoryNameFrom, categoryNameTo, productName);
    }

    @Override
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        Assert.notNull(categoryNameFrom, "Category name from is empty");
        Assert.notNull(categoryNameTo, "Category name to is empty");
        categoryClient.moveAllProducts(categoryNameFrom, categoryNameTo);
    }
}
