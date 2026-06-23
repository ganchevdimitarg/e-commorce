package com.concordeu.catalog.category;

import com.concordeu.catalog.repository.CategoryRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.exception.ConflictException;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.exception.ValidationException;
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
    CategoryResponseDto categoryResponseDto;

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryRepository, productRepository, mapStructMapper, new SimpleMeterRegistry());
        categoryName = "bbbbb";
        categoryResponseDto = new CategoryResponseDto("1", categoryName, new ArrayList<>());
    }

    @Test
    void should_createCategory_when_nameIsNotEmpty() {

        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(categoryResponseDto);

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(argumentCaptor.capture());

        Category category = argumentCaptor.getValue();
        assertThat(category).isNotNull();
        assertThat(category.getName()).isEqualTo(categoryName);
    }

    @Test
    void should_throwValidation_when_createCategoryWithEmptyName() {
        categoryResponseDto = new CategoryResponseDto("1", "", new ArrayList<>());
        assertThatThrownBy(() -> testService.createCategory(categoryResponseDto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Category name is empty: ");

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_throwConflict_when_createCategoryWithExistingName() {
        Category existingCategory = new Category();
        existingCategory.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(existingCategory));

        assertThatThrownBy(() -> testService.createCategory(categoryResponseDto))
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
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouseProduct));

        Category categoryTo = new Category();
        categoryTo.setName("acc");

        when(categoryRepository.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));

        when(categoryRepository.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryRepository.getReferenceById(any())).thenReturn(categoryFrom);

        when(productRepository.changeCategory(any(), any(), anyLong())).thenReturn(1);

        testService.moveOneProduct("pc", "acc", "mouse");

        verify(productRepository).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwOptimisticLock_when_moveProductVersionMismatch() {
        Product mouseProduct = new Product();
        mouseProduct.setName("mouse");
        mouseProduct.setVersion(1L);
        Category categoryFrom = new Category();
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouseProduct));

        Category categoryTo = new Category();
        categoryTo.setName("acc");

        when(categoryRepository.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));
        when(categoryRepository.getReferenceById(any())).thenReturn(categoryFrom);
        when(productRepository.changeCategory(any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> testService.moveOneProduct("pc", "acc", "mouse"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_throwNotFound_when_moveOneProductThatDoesNotExist() {
        Product emptyNameProduct = new Product();
        emptyNameProduct.setName("");
        Category categoryFrom = new Category();
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(emptyNameProduct));
        Category categoryTo = new Category();
        categoryTo.setName("acc");

        when(categoryRepository.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));

        when(categoryRepository.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryRepository.getReferenceById(any())).thenReturn(categoryFrom);

        assertThatThrownBy(() -> testService.moveOneProduct(categoryFrom.getName(), categoryTo.getName(), "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: mouse");

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithEmptyFirstCategoryName() {
        assertThatThrownBy(() -> testService.moveOneProduct("", "acc", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithEmptySecondCategoryName() {
        Category categoryForMove = new Category();
        categoryForMove.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(categoryForMove));

        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentFirstCategory() {
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: " + categoryName);

        verify(productRepository, never()).changeCategory(any(), any(), anyLong());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentSecondCategory() {
        Category categoryForNonExistent = new Category();
        categoryForNonExistent.setName(categoryName);
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(categoryForNonExistent));
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
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

        testService.getCategoriesByPage(1, 5);
        verify(categoryRepository).findAll(pageRequest);
    }

    @Test
    void should_throwValidation_when_deleteCategoryWithEmptyName() {
        assertThatThrownBy(() -> testService.deleteCategory(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Category name is empty: ");

        verify(categoryRepository, never()).deleteByName(any());
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
    void should_moveAllProducts_when_bothCategoriesExist() {
        Product mouse = new Product();
        mouse.setName("mouse");
        mouse.setVersion(1L);
        Product keyboard = new Product();
        keyboard.setName("keyboard");
        keyboard.setVersion(2L);

        Category categoryFrom = new Category();
        categoryFrom.setId("from-id");
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouse, keyboard));

        Category categoryTo = new Category();
        categoryTo.setId("to-id");
        categoryTo.setName("acc");
        categoryTo.setProducts(new ArrayList<>());

        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName("acc")).thenReturn(Optional.of(categoryTo));
        when(categoryRepository.getReferenceById(any())).thenReturn(categoryFrom);
        when(productRepository.changeCategory(any(), any(), anyLong())).thenReturn(1);

        testService.moveAllProducts("pc", "acc");

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> categoryIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> versionCaptor = ArgumentCaptor.forClass(Long.class);
        verify(productRepository, times(2)).changeCategory(nameCaptor.capture(), categoryIdCaptor.capture(), versionCaptor.capture());

        org.assertj.core.api.Assertions.assertThat(nameCaptor.getAllValues()).containsExactlyInAnyOrder("mouse", "keyboard");
        org.assertj.core.api.Assertions.assertThat(categoryIdCaptor.getAllValues()).containsExactly("to-id", "to-id");
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

        service.createCategory(categoryResponseDto);

        assertThat(registry.get("catalog.category.created").counter().count()).isEqualTo(1.0);
    }
}
