package com.concordeu.client.catalog.category;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "category", url = "http://localhost:8081/api/v1/category")
public interface CategoryClient {

    @PostMapping("/create-category")
    CategoryResponseDto createCategory(@RequestBody CategoryRequestDto requestDto);

    @DeleteMapping("/delete")
    void deleteCategory(@RequestBody CategoryRequestDto requestDto);

    @GetMapping("/get-categories")
    Page<CategoryResponseDto> getCategories(@RequestParam int page, @RequestParam int pageSize);

    @PostMapping("/move-one-product")
    void moveOneProduct(
            @RequestParam String categoryNameFrom, @RequestParam String categoryNameTo, @RequestParam String productName);

    @PostMapping("/move-all-products")
    void moveAllProducts(@RequestParam String categoryNameFrom, @RequestParam String categoryNameTo);


}
