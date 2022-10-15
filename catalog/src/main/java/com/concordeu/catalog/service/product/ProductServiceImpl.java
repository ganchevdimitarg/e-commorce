package com.concordeu.catalog.service.product;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.dao.CategoryRepository;
import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.validator.ProductDataValidator;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.dao.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductDataValidator validator;
    private final MapStructMapper mapper;

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

        Product product = mapper.mapDtoToProduct(productDto);
        product.setCategory(category);
        product.setInStock(true);

        log.info("The product " + product.getName() + " is save successful");
        productRepository.saveAndFlush(product);

        return mapper.mapProductToDto(product);
    }

    @Override
    public Page<ProductDto> getProductsByPage(int page, int size) {
        Page<ProductDto> products = productRepository
                .findAll(PageRequest.of(page, size))
                .map(ProductDto::convertProduct);
        log.info("Successful get products: " + products.getSize());

        return products;
    }

    @Override
    public Page<ProductDto> getProductsByCategoryByPage(int page, int size, String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName));

        Page<ProductDto> products = productRepository
                .findAllByCategoryIdByPage(category.getId(), PageRequest.of(page, size))
                .map(ProductDto::convertProduct);
        log.info("Successful get products by category: " + categoryName);

        return products;
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
