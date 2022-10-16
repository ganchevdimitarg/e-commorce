package com.concordeu.catalog.controller;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.CategoryDto;
import com.concordeu.catalog.dto.CategoryRequestDto;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.service.category.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;
    private final MapStructMapper mapper;

    @PostMapping("/create-category")
    public CategoryDto createCategory(@RequestBody CategoryRequestDto requestDto) {
        return categoryService.createCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }

    @DeleteMapping("/delete")
    public void deleteCategory(@RequestBody CategoryRequestDto requestDto) {
        categoryService.deleteCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }

    @GetMapping("/get-categories")
    public Page<CategoryDto> getCategories(@RequestParam int page, @RequestParam int pageSize) {
        return categoryService.getCategoriesByPage(page, pageSize);
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
