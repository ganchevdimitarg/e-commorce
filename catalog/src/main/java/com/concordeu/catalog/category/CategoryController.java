package com.concordeu.catalog.category;

import com.concordeu.catalog.product.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/create-category")
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto categoryDto) {
        CategoryDto category = categoryService.createCategory(categoryDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ProductDto> deleteCategory(@RequestBody CategoryDto categoryDto) {
        categoryService.deleteCategory(categoryDto);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/get-categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.status(HttpStatus.OK).body(categoryService.getCategories());
    }

    @PostMapping("/move-one-product")
    public ResponseEntity<CategoryDto> moveAlOneProduct(@RequestBody Map<String, String> categories) {
        CategoryDto categoryFrom = categoryService.getCategory(categories.get("categoryFrom"));
        CategoryDto categoryTo = categoryService.getCategory(categories.get("categoryTo"));

        categoryService.moveOneProduct(categoryFrom, categoryTo, categories.get("productName"));

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).build();
    }

    @PostMapping("/move-all-products")
    public ResponseEntity<CategoryDto> moveAllProducts(@RequestBody Map<String, String> categories) {
        CategoryDto categoryFrom = categoryService.getCategory(categories.get("categoryFrom"));
        CategoryDto categoryTo = categoryService.getCategory(categories.get("categoryTo"));

        categoryService.moveAllProducts(categoryFrom, categoryTo);

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).build();
    }
}
