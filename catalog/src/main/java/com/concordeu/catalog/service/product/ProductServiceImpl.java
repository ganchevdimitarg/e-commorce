package com.concordeu.catalog.service.product;

import com.concordeu.catalog.repository.CategoryRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import com.concordeu.catalog.concurrency.VirtualThreads;
import com.concordeu.catalog.event.ProductEventPublisher;
import com.concordeu.catalog.exception.ConflictException;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.mapper.MapStructMapper;
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
        publishAfterCommit(() -> productEventPublisher.publishCreated(product.getId(), product.getName()));

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
    public ProductResponseDto getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        return mapper.mapProductToProductResponseDto(product);
    }


    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void updateProduct(String id, UpdateProductCommand command) {
        Product existing = findProductById(id);

        int updated = productRepository.updateById(id,
                command.description(), command.price(), command.characteristics(),
                command.inStock(), existing.getVersion());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), id);
        }
        meterRegistry.counter("catalog.product.updated").increment();
        publishAfterCommit(() -> productEventPublisher.publishUpdated(id, existing.getName()));
        log.info("The updates were successful on product: {}", id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void deleteProduct(String id) {
        Product existing = findProductById(id);
        productRepository.delete(existing);   // honours @SQLDelete soft-delete
        meterRegistry.counter("catalog.product.deleted").increment();
        publishAfterCommit(() -> productEventPublisher.publishDeleted(id, existing.getName()));
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public List<ProductResponseDto> getProductsById(ItemRequestDto product) {
        // Each id is fetched as an independent read on its own virtual thread via
        // VirtualThreads#mapParallel. Because this is a self-invocation (this::getProductById),
        // the proxy-applied @Transactional/@Cacheable on getProductById do NOT apply here — each
        // lookup is a plain auto-commit read. A missing id still surfaces as NotFoundException
        // (fail-fast via VirtualThreads#mapParallel).
        return VirtualThreads.mapParallel(product.items(), this::getProductById);
    }

    private Product findProductById(String id) {
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
                mapper.mapCategoryToCategoryResponseDto(product.getCategory()),
                mapper.mapCommentsToCommentResponseDtos(product.getComments()));
    }
}
