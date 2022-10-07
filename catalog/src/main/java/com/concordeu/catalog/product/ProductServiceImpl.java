package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductDataValidator validator;
    private final ProductMapper productMapper;

    @Override
    public ProductDTO createProduct(ProductDTO productDTO, String categoryName) {
        if (!validator.isValid(productDTO) || categoryName.isEmpty()) {
            log.debug("The product data is not correct! Try again!");
            throw new IllegalArgumentException("The product data is not correct! Try again!");
        }

        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.error("No such category!");
                    throw new IllegalArgumentException("No such category!");
                });

        Product product = productMapper.mapDTOToProduct(productDTO);
        product.setCategory(category);

        productRepository.saveAndFlush(product);

        return productMapper.mapProductToDto(product);
    }

    @Override
    public List<ProductDTO> getProducts(String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category"));
        List<Product> products = productRepository.findAllByCategoryOrderByNameAsc(category);
        return productMapper.mapProductsToDtos(products);
    }

    @Override
    public ProductDTO updateProduct(String productName) {
        return null;
    }

    @Override
    public ProductDTO deleteProduct(String productName) {
        return null;
    }
}
