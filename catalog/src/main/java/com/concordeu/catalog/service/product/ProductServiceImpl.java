package com.concordeu.catalog.service.product;

import com.concordeu.catalog.repository.CategoryRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.exception.ConflictException;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.exception.ValidationException;
import com.concordeu.catalog.mapper.MapStructMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MapStructMapper mapper;
    private final MeterRegistry meterRegistry;

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public ProductResponseDto createProduct(ProductResponseDto productResponseDto, String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> {
                    log.warn("No such category: {}", categoryName);
                    return new NotFoundException("Category", categoryName);
                });

        if (productRepository.findByName(productResponseDto.name()).isPresent()) {
            log.warn("Product with the name: {} already exists.", productResponseDto.name());
            throw new ConflictException("Product with the name: " + productResponseDto.name() + " already exist.");
        }

        Product product = mapper.mapProductResponseDtoToProduct(productResponseDto);
        product.setCategory(category);
        product.setInStock(true);

        log.info("The product {} is save successful", product.getName());
        productRepository.saveAndFlush(product);
        meterRegistry.counter("catalog.product.created").increment();

        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProductsByPage(int page, int size) {
        Page<ProductResponseDto> products = productRepository
                .findAll(PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products: {}", products.getSize());

        return products;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProductsByCategoryByPage(int page, int size, String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new NotFoundException("Category", categoryName));

        Page<ProductResponseDto> products = productRepository
                .findAllByCategoryIdByPage(category.getId(), PageRequest.of(page, size))
                .map(this::convertProduct);
        log.info("Successful get products by category: {}", categoryName);

        return products;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductResponseDto getProductByName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is empty");
        }
        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Product", name));
        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Cacheable(cacheNames = "product", key = "#id")
    public ProductResponseDto getProductById(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Id is empty");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        return mapper.mapProductToProductResponseDto(product);
    }


    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", allEntries = true, beforeInvocation = true)
    public void updateProduct(ProductResponseDto productResponseDto, String productName) {
        checkExistenceProduct(productName);

        productRepository.update(productName,
                productResponseDto.description(),
                productResponseDto.price(),
                productResponseDto.characteristics(),
                productResponseDto.inStock());
        meterRegistry.counter("catalog.product.updated").increment();
        log.info("The updates were successful on product: {}", productName);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", allEntries = true, beforeInvocation = true)
    public void deleteProduct(String productName) {
        checkExistenceProduct(productName);
        productRepository.deleteByName(productName);
        meterRegistry.counter("catalog.product.deleted").increment();
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public List<ProductResponseDto> getProductsById(ItemRequestDto product) {
        return product.items().stream().map(this::getProductById).collect(Collectors.toList());
    }


    private void checkExistenceProduct(String productName) {
        if (productRepository.findByName(productName).isEmpty()) {
            log.warn("Product with the name: {} does not exist.", productName);
            throw new NotFoundException("Product", productName);
        }
    }

    public ProductResponseDto convertProduct(Product product){
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isInStock(),
                product.getCharacteristics(),
                mapper.mapCategoryToCategoryResponseDto(product.getCategory()),
                mapper.mapCommentsToCommentResponseDtos(product.getComments()));
    }
}
