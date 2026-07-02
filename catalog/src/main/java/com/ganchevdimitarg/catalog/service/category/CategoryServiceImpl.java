package com.ganchevdimitarg.catalog.service.category;

import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.category.CategoryResponseDto;
import com.ganchevdimitarg.catalog.dto.category.CreateCategoryCommand;
import com.ganchevdimitarg.catalog.dto.category.MoveProductCommand;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MapStructMapper mapper;
    private final MeterRegistry meterRegistry;

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
        return mapper.mapCategoryToCategoryResponseDto(category);
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
        requireCategory(categoryName);
        categoryRepository.deleteByName(categoryName);
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
        meterRegistry.counter("catalog.category.moved").increment();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        Category from = requireCategory(categoryNameFrom);
        Category to = requireCategory(categoryNameTo);

        int moved = productRepository.moveAllProductsToCategory(from.getId(), to.getId());
        meterRegistry.counter("catalog.category.moved").increment(moved);
        log.info("Moved {} products from {} to {}", moved, categoryNameFrom, categoryNameTo);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<CategoryResponseDto> getCategoriesByPage(Pageable pageable) {
        Page<CategoryResponseDto> categories = categoryRepository
                .findAll(pageable)
                .map(this::convertCategory);

        log.info("Successful get categories: {}", categories.getSize());

        return categories;
    }

    private Category requireCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Category", name));
    }

    private CategoryResponseDto convertCategory(Category category) {
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                mapper.mapProductsToProductRequestDtos(category.getProducts()));
    }
}
