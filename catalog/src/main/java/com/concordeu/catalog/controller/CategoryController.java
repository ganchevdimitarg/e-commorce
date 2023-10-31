package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.service.category.CategoryService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
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
    @Observed(
            name = "user.name",
            contextualName = "createCategory",
            lowCardinalityKeyValues = {"method", "createCategory"}
    )
    public CategoryDTO createCategory(@RequestBody @NonNull CategoryDTO requestDto) {
        if (requestDto.name().isEmpty()) {
            log.debug("Category name is empty: " + requestDto.name());
            throw new IllegalArgumentException("Category name is empty: " + requestDto.name());
        }
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
    @Observed(
            name = "user.name",
            contextualName = "deleteCategory",
            lowCardinalityKeyValues = {"method", "deleteCategory"}
    )
    public void deleteCategory(@RequestParam @NonNull String categoryName) {
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
    @Observed(
            name = "user.name",
            contextualName = "getCategories",
            lowCardinalityKeyValues = {"method", "getCategories"}
    )
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
    @Observed(
            name = "user.name",
            contextualName = "moveOneProduct",
            lowCardinalityKeyValues = {"method", "moveOneProduct"}
    )
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
    @Observed(
            name = "user.name",
            contextualName = "moveAllProducts",
            lowCardinalityKeyValues = {"method", "moveAllProducts"}
    )
    public void moveAllProducts(@RequestParam String categoryNameFrom, @RequestParam String categoryNameTo) {
        categoryService.moveAllProducts(categoryNameFrom, categoryNameTo);
    }


}
