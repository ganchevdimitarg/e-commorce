package com.concordeu.catalog.category;

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
import com.concordeu.catalog.service.category.CategoryService;
import com.concordeu.catalog.service.category.CategoryServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private CategoryService testService;

    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    MapStructMapper mapStructMapper;

    String categoryName;

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryRepository, productRepository, mapStructMapper, new SimpleMeterRegistry());
        categoryName = "bbbbb";
    }

    @Test
    void should_createCategory_when_nameUnique() {
        when(categoryRepository.findByName("PC")).thenReturn(Optional.empty());
        Category saved = new Category();
        saved.setId("c1");
        saved.setName("PC");
        when(categoryRepository.saveAndFlush(any())).thenReturn(saved);
        when(mapStructMapper.mapCategoryToCategoryResponseDto(saved))
                .thenReturn(new CategoryResponseDto("c1", "PC", List.of()));

        CategoryResponseDto result = testService.createCategory(new CreateCategoryCommand("PC"));

        assertThat(result.name()).isEqualTo("PC");
    }

    @Test
    void should_createCategory_when_nameIsNotEmpty() {
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(new CreateCategoryCommand(categoryName));

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(argumentCaptor.capture());

        Category category = argumentCaptor.getValue();
        assertThat(category).isNotNull();
        assertThat(category.getName()).isEqualTo(categoryName);
    }

    @Test
    void should_throwConflict_when_createCategoryWithExistingName() {
        Category existingCategory = new Category();
        existingCategory.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(existingCategory));

        assertThatThrownBy(() -> testService.createCategory(new CreateCategoryCommand(categoryName)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Category with the name: " + categoryName + " already exist.");

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_deleteCategory_when_categoryExists() {
        Category categoryToDelete = new Category();
        categoryToDelete.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(categoryToDelete));

        testService.deleteCategory(categoryName);

        verify(categoryRepository).deleteByName(categoryName);
    }

    @Test
    void should_throwNotFound_when_deleteCategoryThatDoesNotExist() {
        assertThatThrownBy(() -> testService.deleteCategory(categoryName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: " + categoryName);

        verify(categoryRepository, never()).deleteByName(any());
    }

    @Test
    void should_moveProduct_when_bothCategoriesExist() {
        Product mouseProduct = new Product();
        mouseProduct.setName("mouse");
        mouseProduct.setVersion(1L);
        Category categoryFrom = new Category();
        categoryFrom.setId("from-id");
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouseProduct));

        Category categoryTo = new Category();
        categoryTo.setId("to-id");
        categoryTo.setName("acc");

        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName("acc")).thenReturn(Optional.of(categoryTo));
        when(productRepository.findByNameAndCategoryId("mouse", "from-id")).thenReturn(Optional.of(mouseProduct));
        when(productRepository.changeCategory(any(), any(), anyLong())).thenReturn(1);

        testService.moveOneProduct(new MoveProductCommand("pc", "acc", "mouse"));

        verify(productRepository).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwOptimisticLock_when_moveProductVersionMismatch() {
        Product mouseProduct = new Product();
        mouseProduct.setName("mouse");
        mouseProduct.setVersion(1L);
        Category categoryFrom = new Category();
        categoryFrom.setId("from-id");
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouseProduct));

        Category categoryTo = new Category();
        categoryTo.setId("to-id");
        categoryTo.setName("acc");

        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName("acc")).thenReturn(Optional.of(categoryTo));
        when(productRepository.findByNameAndCategoryId("mouse", "from-id")).thenReturn(Optional.of(mouseProduct));
        when(productRepository.changeCategory(any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> testService.moveOneProduct(new MoveProductCommand("pc", "acc", "mouse")))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_throwNotFound_when_moveOneProductThatDoesNotExist() {
        Category categoryFrom = new Category();
        categoryFrom.setId("from-id");
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of());
        Category categoryTo = new Category();
        categoryTo.setId("to-id");
        categoryTo.setName("acc");

        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName("acc")).thenReturn(Optional.of(categoryTo));
        when(productRepository.findByNameAndCategoryId("mouse", "from-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.moveOneProduct(new MoveProductCommand("pc", "acc", "mouse")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: mouse");

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentFirstCategory() {
        assertThatThrownBy(() -> testService.moveOneProduct(new MoveProductCommand(categoryName, "aaaaa", "mouse")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: " + categoryName);

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentSecondCategory() {
        Category categoryForNonExistent = new Category();
        categoryForNonExistent.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(categoryForNonExistent));
        assertThatThrownBy(() -> testService.moveOneProduct(new MoveProductCommand(categoryName, "aaaaa", "mouse")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: aaaaa");

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_returnCategories_when_pageRequested() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Category> products = Arrays.asList(new Category(), new Category());
        Page<Category> page = new PageImpl<>(products, pageRequest, products.size());
        when(categoryRepository.findAll(pageRequest)).thenReturn(page);

        testService.getCategoriesByPage(pageRequest);
        verify(categoryRepository).findAll(pageRequest);
    }

    @Test
    void should_returnCategory_when_getCategoryByName() {
        Category category = new Category();
        category.setName(categoryName);
        category.setProducts(new ArrayList<>());
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(category));

        CategoryResponseDto result = testService.getCategory(categoryName);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(categoryName);
    }

    @Test
    void should_throwNotFound_when_getCategoryByNonExistentName() {
        assertThatThrownBy(() -> testService.getCategory("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: nonexistent");
    }

    @Test
    void should_issueSingleBulkUpdate_when_moveAllProducts() {
        Category from = new Category();
        from.setId("from-1");
        from.setName("PC");
        Category to = new Category();
        to.setId("to-1");
        to.setName("Laptop");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(from));
        when(categoryRepository.findByName("Laptop")).thenReturn(Optional.of(to));
        when(productRepository.moveAllProductsToCategory("from-1", "to-1")).thenReturn(3);

        testService.moveAllProducts("PC", "Laptop");

        verify(productRepository, times(1)).moveAllProductsToCategory("from-1", "to-1");
        verify(productRepository, never()).changeCategory(anyString(), anyString(), anyLong());
    }

    @Test
    void should_incrementCreatedCounter_when_categoryCreated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CategoryServiceImpl service =
                new CategoryServiceImpl(categoryRepository, productRepository, mapStructMapper, registry);

        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.empty());
        Category saved = new Category();
        saved.setName(categoryName);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenReturn(saved);

        service.createCategory(new CreateCategoryCommand(categoryName));

        assertThat(registry.get("catalog.category.created").counter().count()).isEqualTo(1.0);
    }
}
