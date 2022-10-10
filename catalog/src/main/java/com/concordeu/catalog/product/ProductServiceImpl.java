package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryRepository;
import com.concordeu.catalog.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductDataValidator validator;
    private final ModelMapper modelMapper;

    @Override
    public ProductDto createProduct(ProductDto productDto, String categoryName) {
        validator.validateData(productDto, categoryName);

        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.error("No such category: " + categoryName);
                    throw new IllegalArgumentException("No such category: " + categoryName);
                });

        if (productRepository.findByName(productDto.getName()).isPresent()) {
            log.error("Product with the name: " + productDto.getName() + " already exists.");
            throw new IllegalArgumentException("Product with the name: " + productDto.getName() + " already exist.");
        }

        Product product = modelMapper.mapDtoToProduct(productDto);
        product.setCategory(category);
        product.setInStock(true);

        productRepository.saveAndFlush(product);
        log.info("The product "+ product.getName() + " is save successful");

        return modelMapper.mapProductToDto(product);
    }

    @Override
    public List<ProductDto> getProducts() {

        List<Product> products = productRepository.findAll();
        log.info("Successful get products");

        return modelMapper.mapProductsToDtos(products);
    }

    @Override
    public List<ProductDto> getProductsByCategory(String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: "+ categoryName));

        List<Product> products = productRepository.findAllByCategoryOrderByNameAsc(category);
        log.info("Successful get products by category: " + categoryName);

        return modelMapper.mapProductsToDtos(products);
    }

    @Override
    public void updateProduct(ProductDto productDto, String productName) {
        validator.validateData(productDto, productName);

        checkExistenceProduct(productName);

        productRepository.update(productName,
                productDto.getDescription(),
                productDto.getPrice(),
                productDto.getCharacteristics(),
                productDto.isInStock());
        log.info("The updates were successful on product: " + productName);
    }

    @Override
    public void deleteProduct(String productName) {
        checkExistenceProduct(productName);
        productRepository.deleteByName(productName);
    }


    private void checkExistenceProduct(String productName) {
        if (productRepository.findByName(productName).isEmpty()) {
            log.error("Product with the name: " + productName + " does not exist.");
            throw new IllegalArgumentException("Product with the name: " + productName + " does not exist.");
        }
    }
}
