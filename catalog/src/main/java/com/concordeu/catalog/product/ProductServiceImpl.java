package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
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
        checkInputData(productDTO);

        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.error("No such category: " + categoryName);
                    throw new IllegalArgumentException("No such category: " + categoryName);
                });

        Product product = productMapper.mapDTOToProduct(productDTO);
        product.setCategory(category);

        productRepository.saveAndFlush(product);
        log.info("The product "+ product.getName() + " is save successful");

        return productMapper.mapProductToDto(product);
    }

    @Override
    public List<ProductDTO> getProducts() {

        List<Product> products = productRepository.findAll();
        log.info("Successful get products");

        return productMapper.mapProductsToDtos(products);
    }

    @Override
    public List<ProductDTO> getProductsByCategory(String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: "+ categoryName));

        List<Product> products = productRepository.findAllByCategoryOrderByNameAsc(category);
        log.info("Successful get products by category: " + categoryName);

        return productMapper.mapProductsToDtos(products);
    }

    @Override
    public void updateProduct(String productName, ProductDTO productDTO) {
        checkInputData(productDTO);
        productRepository.update(productName,
                productDTO.getDescription(),
                productDTO.getPrice(),
                productDTO.getCharacteristics(),
                productDTO.isInStock());
        log.info("The updates were successful on product: " + productName);
    }

    @Override
    public ProductDTO deleteProduct(String productName) {
        Product product = productRepository.deleteByName(productName);
        return productMapper.mapProductToDto(product);
    }

    private void checkInputData(ProductDTO productDTO) {
        if (!validator.isValid(productDTO)) {
            log.debug("The product data is not correct! Try again!");
            throw new IllegalArgumentException("The product data is not correct! Try again!");
        }
    }
}
