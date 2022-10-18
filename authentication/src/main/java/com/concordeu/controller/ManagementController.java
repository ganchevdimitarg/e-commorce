package com.concordeu.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/management/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ManagementController {

    @GetMapping("/get")
    public String getString(Principal principal){
        return String.format("You did it Admin: %s -> %s!!!", principal.getName(), principal);
    }

   /*@PostMapping("/create-category")
   @PreAuthorize("hasAnyAuthority('create:category')")
    public CategoryDto createCategory(@RequestBody CategoryRequestDto requestDto) {
        return categoryService.createCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }*/

    /*@DeleteMapping("/delete")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public void deleteCategory(@RequestBody CategoryRequestDto requestDto) {
        categoryService.deleteCategory(mapper.mapCategoryRequestDtoToCategoryDto(requestDto));
    }

    @GetMapping("/get-categories")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<CategoryDto> getCategories(@RequestParam int page, @RequestParam int pageSize) {
        return categoryService.getCategoriesByPage(page, pageSize);
    }

    @PostMapping("/move-one-product")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void moveOneProduct(
            @RequestParam String categoryNameFrom, @RequestParam String categoryNameTo, @RequestParam String productName) {
        categoryService.moveOneProduct(categoryNameFrom, categoryNameTo, productName);
    }

    @PostMapping("/move-all-products")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void moveAllProducts(@RequestParam String categoryNameFrom, @RequestParam String categoryNameTo) {
        categoryService.moveAllProducts(categoryNameFrom, categoryNameTo);
    }

    @PostMapping("/create-product/{categoryName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ProductDto createProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String categoryName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        return productService.createProduct(productDto, categoryName);
    }

    @GetMapping("/get-products")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<ProductDto> getProducts(@RequestParam int page, @RequestParam int pageSize) {
        return productService.getProductsByPage(page, pageSize);
    }

    @GetMapping("/get-products/{categoryName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<ProductDto> getProductsByCategory(@RequestParam int page, @RequestParam int pageSize, @PathVariable String categoryName) {
        return productService.getProductsByCategoryByPage(page, pageSize, categoryName);
    }

    @PutMapping("/update-product/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void updateProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String productName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        productService.updateProduct(productDto, productName);
    }

    @DeleteMapping("/delete/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public void deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);
    }

    @PostMapping("/create-comment/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public CommentDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        CommentDto commentDto = mapper.mapCommentRequestDtoToCommentDto(requestDto);
        validator.validateData(commentDto);
        return commentService.createComment(commentDto, productName);
    }

    @GetMapping("/get-comments-product-name/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Page<CommentDto> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName) {
        return commentService.findAllByProductNameByPage(productName, page, pageSize);
    }

    @GetMapping("/get-comments-author/{author}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Page<CommentDto> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author) {
        return commentService.findAllByAuthorByPage(author, page, pageSize);
    }

    @GetMapping("/get-avg-stars/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public double getAvgStars(@PathVariable String productName) {
        return commentService.getAvgStars(productName);
    }*/

}
