package com.ganchevdimitarg.catalog.service.product;

import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.ProductEventPublisher;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MapStructMapper mapper;
    private final MeterRegistry meterRegistry;
    private final ProductEventPublisher productEventPublisher;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public ProductResponseDto createProduct(CreateProductCommand command) {
        Category category = categoryRepository.findByName(command.categoryName())
                .orElseThrow(() -> {
                    log.warn("No such category: {}", command.categoryName());
                    return new NotFoundException("Category", command.categoryName());
                });

        if (productRepository.findByName(command.name()).isPresent()) {
            log.warn("Product with the name: {} already exists.", command.name());
            throw new ConflictException("Product with the name: " + command.name() + " already exist.");
        }

        Product product = mapper.mapCreateCommandToProduct(command);
        product.setCategory(category);

        productRepository.saveAndFlush(product);
        log.info("The product {} is save successful", product.getName());
        meterRegistry.counter("catalog.product.created").increment();
        publishAfterCommit(() -> productEventPublisher.publishCreated(product.getId().toString(), product.getName()));

        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProductsByPage(Pageable pageable) {
        Page<ProductResponseDto> products = productRepository
                .findAll(pageable)
                .map(this::convertProduct);
        log.info("Successful get products: {}", products.getSize());

        return products;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProductsByCategoryByPage(Pageable pageable, String categoryName) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new NotFoundException("Category", categoryName));

        Page<ProductResponseDto> products = productRepository
                .findAllByCategoryIdByPage(category.getId(), pageable)
                .map(this::convertProduct);
        log.info("Successful get products by category: {}", categoryName);

        return products;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductResponseDto getProductByName(String name) {
        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Product", name));
        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Cacheable(cacheNames = "product", key = "#id")
    public ProductResponseDto getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        return mapper.mapProductToProductResponseDto(product);
    }


    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void updateProduct(UUID id, UpdateProductCommand command) {
        Product existing = findProductById(id);

        int updated = productRepository.updateById(id,
                command.description(), command.price(), command.characteristics(),
                command.inStock(), existing.getVersion());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), id);
        }
        meterRegistry.counter("catalog.product.updated").increment();
        publishAfterCommit(() -> productEventPublisher.publishUpdated(id.toString(), existing.getName()));
        log.info("The updates were successful on product: {}", id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void deleteProduct(UUID id) {
        Product existing = findProductById(id);
        productRepository.delete(existing);   // honours @SQLDelete soft-delete
        meterRegistry.counter("catalog.product.deleted").increment();
        publishAfterCommit(() -> productEventPublisher.publishDeleted(id.toString(), existing.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public List<ProductResponseDto> getProductsById(ItemRequestDto product) {
        // One query for the whole batch; the transaction keeps the lazy comments collection
        // and the category association usable while mapping, and findAllById order is
        // unspecified so the response is rebuilt in request order.
        List<UUID> requested = product.items();
        Map<UUID, Product> found = productRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<UUID> missing = requested.stream()
                .filter(id -> !found.containsKey(id))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Products", missing);
        }
        return requested.stream()
                .map(id -> convertProduct(found.get(id)))
                .toList();
    }

    private Product findProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product with the id: {} does not exist.", id);
                    return new NotFoundException("Product", id);
                });
    }

    private void publishAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
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
                mapper.mapCategoryToCategorySummaryDto(product.getCategory()),
                mapper.mapCommentsToCommentResponseDtos(product.getComments()));
    }
}
