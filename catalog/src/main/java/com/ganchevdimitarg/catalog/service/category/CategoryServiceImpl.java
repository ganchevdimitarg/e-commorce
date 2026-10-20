package com.ganchevdimitarg.catalog.service.category;

import com.ganchevdimitarg.catalog.cache.ProductCacheEvictor;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.category.CategoryResponseDto;
import com.ganchevdimitarg.catalog.dto.category.CreateCategoryCommand;
import com.ganchevdimitarg.catalog.dto.category.MoveProductCommand;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MeterRegistry meterRegistry;
    private final ProductCacheEvictor productCacheEvictor;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CategoryResponseDto createCategory(CreateCategoryCommand command) {
        if (categoryRepository.findByName(command.name()).isPresent()) {
            log.warn("Category with the name: {} already exist.", command.name());
            throw new ConflictException("Category with the name: " + command.name() + " already exist.");
        }
        Category category = new Category();
        category.setName(command.name());
        category = categoryRepository.saveAndFlush(category);
        meterRegistry.counter("catalog.category.created").increment();
        return convertCategory(category);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public CategoryResponseDto getCategory(String categoryName) {
        Category category = requireCategory(categoryName);
        return convertCategory(category);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void deleteCategory(String categoryName) {
        Category category = requireCategory(categoryName);
        if (productRepository.existsByCategory_Id(category.getId())) {
            throw new ConflictException(
                    "Category " + categoryName + " still contains products; move them first");
        }
        categoryRepository.delete(category);
        productCacheEvictor.evictAll();
        meterRegistry.counter("catalog.category.deleted").increment();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveOneProduct(MoveProductCommand command) {
        Category from = requireCategory(command.categoryNameFrom());
        Category to = requireCategory(command.categoryNameTo());

        Product product = productRepository
                .findByNameAndCategoryId(command.productName(), from.getId())
                .orElseThrow(() -> {
                    log.warn("No such product: {}", command.productName());
                    return new NotFoundException("Product", command.productName());
                });

        int updated = productRepository.changeCategory(product.getName(), to.getId(), product.getVersion());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), product.getName());
        }
        productCacheEvictor.evict(product.getId());
        meterRegistry.counter("catalog.category.moved").increment();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        Category from = requireCategory(categoryNameFrom);
        Category to = requireCategory(categoryNameTo);

        int moved = productRepository.moveAllProductsToCategory(from.getId(), to.getId());
        productCacheEvictor.evictAll();
        meterRegistry.counter("catalog.category.moved").increment(moved);
        log.info("Moved {} products from {} to {}", moved, categoryNameFrom, categoryNameTo);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CategoryResponseDto> getCategoriesByPage(Pageable pageable) {
        Page<Category> categories = categoryRepository.findAll(pageable);
        if (categories.isEmpty()) {
            // Page.empty(pageable) would reset totalElements/totalPages to 0, which breaks
            // navigation for out-of-range pages; keep the totals the count query returned.
            return new PageImpl<>(List.of(), pageable, categories.getTotalElements());
        }
        Map<UUID, Long> counts = productRepository
                .countProductsByCategoryIds(categories.getContent().stream().map(Category::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductRepository.CategoryProductCount::getCategoryId,
                                          ProductRepository.CategoryProductCount::getProductCount));
        Page<CategoryResponseDto> result = categories.map(category -> new CategoryResponseDto(
                category.getId(), category.getName(), counts.getOrDefault(category.getId(), 0L)));

        log.info("Successful get categories: {}", result.getSize());

        return result;
    }

    private Category requireCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Category", name));
    }

    private CategoryResponseDto convertCategory(Category category) {
        return new CategoryResponseDto(category.getId(), category.getName(),
                productRepository.countByCategory_Id(category.getId()));
    }
}
