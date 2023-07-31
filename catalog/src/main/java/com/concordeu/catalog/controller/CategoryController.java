package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.mapper.CategoryMapper;
import com.concordeu.catalog.service.category.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    @Operation(summary = "Create Category",  description = "Create a category in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-category")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CategoryDTO createCategory(@RequestBody CategoryDTO requestDto) {
        return categoryService.createCategory(requestDto);
    }

    @Operation(summary = "Delete Category",  description = "Delete category from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-category")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void deleteCategory(@RequestParam String categoryName) {
        categoryService.deleteCategory(categoryName);
    }

    @Operation(summary = "Get Categories",  description = "Get all categories from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-categories")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CategoryDTO> getCategories(@RequestParam int page, @RequestParam int size) {
        return categoryService.getCategoriesByPage(page, size);
    }

    @Operation(summary = "Move One Product",  description = "Move one product from one category to another category",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/move-one-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveOneProduct(
            @RequestParam String categoryNameFrom, @RequestParam String categoryNameTo, @RequestParam String productName) {
        categoryService.moveOneProduct(categoryNameFrom, categoryNameTo, productName);
    }

    @Operation(summary = "Move All Products",  description = "Move all products from one category to another category",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/move-all-products")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveAllProducts(@RequestParam String categoryNameFrom, @RequestParam String categoryNameTo) {
        categoryService.moveAllProducts(categoryNameFrom, categoryNameTo);
    }


}
