package com.concordeu.catalog.category;

import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.excaption.ConflictException;
import com.concordeu.catalog.excaption.NotFoundException;
import com.concordeu.catalog.excaption.ValidationException;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.category.CategoryService;
import com.concordeu.catalog.service.category.CategoryServiceImpl;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private CategoryService testService;

    @Mock
    CategoryDao categoryDao;
    @Mock
    ProductDao productDao;
    @Mock
    MapStructMapper mapStructMapper;

    String categoryName;
    CategoryResponseDto categoryResponseDto;

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryDao, productDao, mapStructMapper);
        categoryName = "bbbbb";
        categoryResponseDto = new CategoryResponseDto("1", categoryName, new ArrayList<>());
    }

    @Test
    void createCategoryShouldCreateCategoryIfNameIsNotEmpty() {

        when(categoryDao.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(categoryResponseDto);

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryDao).saveAndFlush(argumentCaptor.capture());

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

        verify(categoryDao, never()).saveAndFlush(any());
    }

    @Test
    void should_throwConflict_when_createCategoryWithExistingName() {
        Category existingCategory = new Category();
        existingCategory.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(existingCategory));

        assertThatThrownBy(() -> testService.createCategory(categoryResponseDto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Category with the name: " + categoryName + " already exist.");

        verify(categoryDao, never()).saveAndFlush(any());
    }

    @Test
    void deleteCategoryShouldDeleteProductIfProductExist() {
        Category categoryToDelete = new Category();
        categoryToDelete.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(categoryToDelete));

        testService.deleteCategory(categoryName);

        verify(categoryDao).deleteByName(categoryName);
    }

    @Test
    void should_throwNotFound_when_deleteCategoryThatDoesNotExist() {
        assertThatThrownBy(() -> testService.deleteCategory(categoryName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: " + categoryName);

        verify(categoryDao, never()).deleteByName(any());
    }

    @Test
    void moveOneProductShouldMoveProductFromOneCategoryToAnotherCategory() {
        Product mouseProduct = new Product();
        mouseProduct.setName("mouse");
        Category categoryFrom = new Category();
        categoryFrom.setName("pc");
        categoryFrom.setProducts(List.of(mouseProduct));

        Category categoryTo = new Category();
        categoryTo.setName("acc");

        when(categoryDao.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));

        when(categoryDao.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryDao.getById(any())).thenReturn(categoryFrom);

        testService.moveOneProduct("pc", "acc", "mouse");

        verify(productDao).changeCategory(any(), any());
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

        when(categoryDao.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));

        when(categoryDao.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryDao.getById(any())).thenReturn(categoryFrom);

        assertThatThrownBy(() -> testService.moveOneProduct(categoryFrom.getName(), categoryTo.getName(), "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: mouse");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithEmptyFirstCategoryName() {
        assertThatThrownBy(() -> testService.moveOneProduct("", "acc", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithEmptySecondCategoryName() {
        Category categoryForMove = new Category();
        categoryForMove.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(categoryForMove));

        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentFirstCategory() {
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: " + categoryName);

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void should_throwNotFound_when_moveOneProductWithNonExistentSecondCategory() {
        Category categoryForNonExistent = new Category();
        categoryForNonExistent.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(categoryForNonExistent));
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: aaaaa");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void getCategoriesShouldReturnAllCategories() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Category> products = Arrays.asList(new Category(), new Category());
        Page<Category> page = new PageImpl<>(products, pageRequest, products.size());
        when(categoryDao.findAll(pageRequest)).thenReturn(page);

        testService.getCategoriesByPage(1, 5);
        verify(categoryDao).findAll(pageRequest);
    }
}
