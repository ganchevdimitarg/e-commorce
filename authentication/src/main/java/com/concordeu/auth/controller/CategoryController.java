package com.concordeu.auth.controller;

import com.concordeu.auth.service.catalog.CategoryService;
import com.concordeu.client.catalog.category.CategoryRequestDto;
import com.concordeu.client.catalog.category.CategoryResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/create-category")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public CategoryResponseDto createCategory(@RequestBody CategoryRequestDto requestDto) {
        return categoryService.createCategory(requestDto);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deleteCategory(@RequestBody CategoryRequestDto requestDto) {
        categoryService.deleteCategory(requestDto);
    }

    @GetMapping("/get-categories")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<CategoryResponseDto> getCategories(@RequestParam int page,
                                                   @RequestParam int pageSize) {
        return categoryService.getCategoriesByPage(page, pageSize);
    }

    @PostMapping("/move-one-product")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void moveOneProduct(@RequestParam String categoryNameFrom,
                               @RequestParam String categoryNameTo,
                               @RequestParam String productName) {
        categoryService.moveOneProduct(categoryNameFrom, categoryNameTo, productName);
    }

    @PostMapping("/move-all-products")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void moveAllProducts(@RequestParam String categoryNameFrom,
                                @RequestParam String categoryNameTo) {
        categoryService.moveAllProducts(categoryNameFrom, categoryNameTo);
    }
}
