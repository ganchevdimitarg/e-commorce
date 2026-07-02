package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.dto.PageResponse;
import com.ganchevdimitarg.catalog.dto.category.CategoryRequestDto;
import com.ganchevdimitarg.catalog.dto.category.CategoryResponseDto;
import com.ganchevdimitarg.catalog.dto.category.CreateCategoryCommand;
import com.ganchevdimitarg.catalog.dto.category.MoveProductCommand;
import com.ganchevdimitarg.catalog.service.category.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Create category", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(@RequestBody @Valid CategoryRequestDto requestDto) {
        CategoryResponseDto created = categoryService.createCategory(
                new CreateCategoryCommand(requestDto.name()));
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + created.name())).body(created);
    }

    @Operation(summary = "Delete category", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteCategory(@PathVariable @NotBlank String name) {
        categoryService.deleteCategory(name);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List categories", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    public PageResponse<CategoryResponseDto> getCategories(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(categoryService.getCategoriesByPage(PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Move one product between categories", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/{from}/products/{productName}:move")
    public ResponseEntity<Void> moveOneProduct(@PathVariable @NotBlank String from,
                                               @PathVariable @NotBlank String productName,
                                               @RequestParam @NotBlank String to) {
        categoryService.moveOneProduct(new MoveProductCommand(from, to, productName));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Move all products between categories", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/{from}/products:move-all")
    public ResponseEntity<Void> moveAllProducts(@PathVariable @NotBlank String from,
                                                @RequestParam @NotBlank String to) {
        categoryService.moveAllProducts(from, to);
        return ResponseEntity.ok().build();
    }
}
