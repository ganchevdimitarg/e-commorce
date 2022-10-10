package com.concordeu.catalog.category;

import com.concordeu.catalog.product.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/create-category")
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto categoryDto) {
        CategoryDto category = categoryService.createCategory(categoryDto.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @DeleteMapping("/delete/{categoryName}")
    public ResponseEntity<ProductDto> deleteCategory(@PathVariable String categoryName) {
        categoryService.deleteCategory(categoryName);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
