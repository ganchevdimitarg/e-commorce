package com.concordeu.catalog.service.category;

import com.concordeu.catalog.repository.CategoryRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.category.CreateCategoryCommand;
import com.concordeu.catalog.dto.category.MoveProductCommand;
import com.concordeu.catalog.exception.ConflictException;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.mapper.MapStructMapper;
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
        if (categoryRepository.findByName(categoryName).isEmpty()) {
            log.warn("No such category: {}", categoryName);
            throw new NotFoundException("Category", categoryName);
        }

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
        Category categoryFrom = requireCategory(categoryNameFrom);

        List<Product> products = categoryFrom.getProducts();
        for (Product product : products) {
            moveOneProduct(new MoveProductCommand(categoryNameFrom, categoryNameTo, product.getName()));
        }
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
