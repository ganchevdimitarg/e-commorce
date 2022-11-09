package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.category.CategoryRequestDto;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.category.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;
    private final MapStructMapper mapper;

    @PostMapping("/create-category")
    public CategoryResponseDto createCategory(@RequestBody CategoryRequestDto requestDto) {
        return categoryService.createCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }

    @DeleteMapping("/delete")
    public void deleteCategory(@RequestBody CategoryRequestDto requestDto) {
        categoryService.deleteCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }

    @GetMapping("/get-categories")
    public Page<CategoryResponseDto> getCategories(@RequestParam int page, @RequestParam int size) {
        return categoryService.getCategoriesByPage(page, size);
    }

    @PostMapping("/move-one-product")
    public void moveOneProduct(
            @RequestParam String categoryNameFrom, @RequestParam String categoryNameTo, @RequestParam String productName) {
        categoryService.moveOneProduct(categoryNameFrom, categoryNameTo, productName);
    }

    @PostMapping("/move-all-products")
    public void moveAllProducts(@RequestParam String categoryNameFrom, @RequestParam String categoryNameTo) {
        categoryService.moveAllProducts(categoryNameFrom, categoryNameTo);
    }


}
