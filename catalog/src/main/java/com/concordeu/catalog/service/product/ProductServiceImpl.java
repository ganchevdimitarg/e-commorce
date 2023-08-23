package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.ItemRequestDTO;
import com.concordeu.catalog.dto.ProductDTO;
import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Product;
import com.concordeu.catalog.mapper.ProductMapper;
import com.concordeu.catalog.repositories.CategoryRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.validator.ProductDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductDataValidator validator;
    private final ProductMapper mapper;

    @Override
    public ProductDTO createProduct(ProductDTO productDTO, String categoryName) {
        validator.validateData(productDTO, categoryName);

        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.warn("No such category: " + categoryName);
                    return new IllegalArgumentException("No such category: " + categoryName);
                });

        if (productRepository.findByName(productDTO.getName()).isPresent()) {
            log.warn("Product with the name: " + productDTO.getName() + " already exists.");
            throw new IllegalArgumentException("Product with the name: " + productDTO.getName() + " already exist.");
        }

        Product product = mapper.mapProductDTOToProduct(productDTO);
        product.setCategory(category);
        product.setInStock(true);

        log.info("The product " + product.getName() + " is save successful");
        productRepository.saveAndFlush(product);

        return mapper.mapProductToProductDTO(product);
    }

    @Override
    @Cacheable(value="Product")
    public Page<ProductDTO> getProductsByPage(int page, int size) {
        Page<ProductDTO> products = productRepository
                .findAll(PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products: " + products.getSize());

        return products;
    }

    @Override
    @Cacheable(value="Product", key="#categoryName", condition="#categoryName!=null")
    public Page<ProductDTO> getProductsByCategoryByPage(int page, int size, String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("No such category: " + categoryName));

        Page<ProductDTO> products = productRepository
                .findAllByCategoryIdByPage(category.getId(), PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products by category: " + categoryName);

        return products;
    }

    @Override
    @Cacheable(value="Product", key="#name", condition="#name!=null")
    public ProductDTO getProductByName(String name) {
        Assert.notNull(name, "Name is empty");
        Product product = productRepository.findByName(name).orElseThrow(() -> new IllegalArgumentException("No such product"));
        return mapper.mapProductToProductDTO(product);
    }

    @Override
    @Cacheable(value="Product", key="#id", condition="#id!=null")
    public ProductDTO getProductById(String id) {
        Assert.notNull(id, "Id is empty");
        Product product = productRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("No such product"));
        return ProductDTO.mapProductToProductDTO(product);
    }

    @Transactional
    @Override
    @CachePut(value="Product", key="#productName", condition="#productName != null")
    public void updateProduct(ProductDTO productDTO, String productName) {
        validator.validateData(productDTO, productName);

        checkExistenceProduct(productName);

        productRepository.update(productName,
                productDTO.getDescription(),
                productDTO.getPrice(),
                productDTO.getCharacteristics(),
                productDTO.isInStock());
        log.info("The updates were successful on product: " + productName);
    }

    @Transactional
    @Override
    @CacheEvict(value="Product", key="#productName", condition="#productName != null")
    public void deleteProduct(String productName) {
        checkExistenceProduct(productName);
        productRepository.deleteByName(productName);
    }

    @Override
    public List<ProductDTO> getProductsById(ItemRequestDTO product) {
        return product.items().stream().map(this::getProductById).collect(Collectors.toList());
    }

    private void checkExistenceProduct(String productName) {
        if (productRepository.findByName(productName).isEmpty()) {
            log.warn("Product with the name: " + productName + " does not exist.");
            throw new IllegalArgumentException("Product with the name: " + productName + " does not exist.");
        }
    }

    public ProductDTO convertProduct(Product product){
        return ProductDTO.mapProductToProductDTO(product);
    }
}
