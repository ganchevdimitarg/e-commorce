package com.ganchevdimitarg.catalog.service.product;

import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.repository.ProductSpecifications;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.OutboxEventWriter;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.exception.PreconditionFailedException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OutboxEventWriter outboxEventWriter;
    private final ProductCache productCache;
    private final AuditorAware<String> auditorAware;

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
        // Written in the same transaction as the product row: the outbox commits or rolls back with it
        outboxEventWriter.writeCreated(product);
        log.info("The product {} is save successful", product.getName());
        meterRegistry.counter("catalog.product.created").increment();

        ProductResponseDto dto = mapper.mapProductToProductResponseDto(product);
        productCache.put(product.getId(), dto);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        UUID categoryId = null;
        if (criteria.categoryName() != null) {
            categoryId = categoryRepository.findByName(criteria.categoryName())
                    .orElseThrow(() -> new NotFoundException("Category", criteria.categoryName()))
                    .getId();
        }
        return productRepository.findAll(ProductSpecifications.matching(criteria, categoryId), pageable)
                .map(this::convertProduct);
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
    public ProductResponseDto updateProduct(UUID id, UpdateProductCommand command, Long expectedVersion) {
        Product existing = findProductById(id);
        if (expectedVersion != null && expectedVersion != existing.getVersion()) {
            throw new PreconditionFailedException("Product", id);
        }

        int updated = productRepository.updateById(id,
                command.description(), command.price(), command.characteristics(),
                command.inStock(), existing.getVersion(), currentAuditor());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), id);
        }
        meterRegistry.counter("catalog.product.updated").increment();

        // Primary read inside the write transaction: the after-commit put caches the new state
        Product refreshed = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        outboxEventWriter.writeUpdated(refreshed);
        ProductResponseDto dto = mapper.mapProductToProductResponseDto(refreshed);
        productCache.put(id, dto);
        log.info("The updates were successful on product: {}", id);
        return dto;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void deleteProduct(UUID id) {
        Product existing = findProductById(id);
        productRepository.delete(existing);   // honours @SQLDelete soft-delete
        outboxEventWriter.writeDeleted(existing);
        productCache.evict(id);
        meterRegistry.counter("catalog.product.deleted").increment();
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

    /**
     * JPQL bulk updates bypass the persistence context, so JPA auditing never fires for
     * them; the auditor is passed explicitly.
     */
    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
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
                mapper.mapCommentsToCommentResponseDtos(product.getComments()),
                product.getVersion());
    }
}
