package com.concordeu.catalog.product;

import com.concordeu.catalog.category.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.xml.catalog.Catalog;
import java.util.List;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CatalogService catalogService;
    private final ProductDataValidator validator;

    @ModelAttribute("createProductRequest")
    private ProductDTO createProduct() {
        return new ProductDTO();
    }

    @PostMapping("/create-product/{category}")
    public ResponseEntity<ProductDTO> createProduct(@Valid @ModelAttribute("createProductRequest") ProductDTO request, BindingResult bindingResult, @PathVariable String category) {
        if (bindingResult.hasErrors()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        productService.createProduct(request, category);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/get-products/{category}")
    public ResponseEntity<List<ProductDTO>> getProducts(@PathVariable String category) {
        return ResponseEntity.ok(productService.getProducts(category));
    }

   /* @PutMapping("/update-product/{productName}")
    public ResponseEntity<ProductDTO> updateProduct(@PathVariable String productName) {
        productService.updateProduct(productName);
        return ResponseEntity.
    }*/
}
