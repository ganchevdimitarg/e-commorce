package com.concordeu.catalog.category;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.dto.CategoryDto;
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

    String categoryName = "pc";

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryDao, productDao, mapStructMapper);
    }

    @Test
    void createCategoryShouldCreateCategoryIfNameIsNotEmpty() {

        when(categoryDao.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(CategoryDto.builder().name(categoryName).build());

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryDao).saveAndFlush(argumentCaptor.capture());

        Category category = argumentCaptor.getValue();
        assertThat(category).isNotNull();
        assertThat(category.getName()).isEqualTo(categoryName);
    }

    @Test
    void createCategoryShouldThrowExceptionIfNameIsEmpty() {
        assertThatThrownBy(() -> testService.createCategory(CategoryDto.builder().name("").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category name is empty: ");

        verify(categoryDao, never()).saveAndFlush(any());
    }

    @Test
    void createCategoryShouldThrowExceptionIfCategoryExist() {
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.createCategory(CategoryDto.builder().name(categoryName).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category with the name: " + categoryName + " already exist.");

        verify(categoryDao, never()).saveAndFlush(any());
    }

    @Test
    void deleteCategoryShouldDeleteProductIfProductExist() {
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        testService.deleteCategory(CategoryDto.builder().name(categoryName).build());

        verify(categoryDao).deleteByName(categoryName);
    }

    @Test
    void deleteCategoryShouldDeleteIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.deleteCategory(CategoryDto.builder().name("bbbbb").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: bbbbb");

        verify(categoryDao, never()).deleteByName(any());
    }

    @Test
    void moveOneProductShouldMoveProductFromOneCategoryToAnotherCategory() {
        Category categoryFrom = Category.builder()
                .name("pc")
                .products(List.of(Product.builder().name("mouse").build()))
                .build();

        Category categoryTo = Category.builder().name("acc").build();

        when(categoryDao.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));
        when(mapStructMapper.mapCategoryToDto(categoryFrom)).thenReturn(CategoryDto.builder().id("1").build());

        when(categoryDao.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));
        when(mapStructMapper.mapCategoryToDto(categoryTo)).thenReturn(CategoryDto.builder().id("2").build());

        when(categoryDao.getById(any())).thenReturn(categoryFrom);

        testService.moveOneProduct("pc", "acc", "mouse");

        verify(productDao).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfProductDoesNotExist() {
        Category categoryFrom = Category.builder().name("pc").products(List.of(Product.builder().name("").build())).build();
        Category categoryTo = Category.builder().name("acc").build();
        when(categoryDao.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));
        when(mapStructMapper.mapCategoryToDto(categoryFrom)).thenReturn(CategoryDto.builder().id("1").build());

        when(categoryDao.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));
        when(mapStructMapper.mapCategoryToDto(categoryTo)).thenReturn(CategoryDto.builder().id("2").build());

        when(categoryDao.getById(any())).thenReturn(categoryFrom);

        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "acc", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: mouse");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfFirstCategoryNameIsEmpty() {
        assertThatThrownBy(() -> testService.moveOneProduct("", "acc", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: ");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfSecondCategoryNameIsEmpty() {
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: ");

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfFirstCategoryDoesNotExist() {
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

        verify(productDao, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfSecondCategoryDoesNotExist() {
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: aaaaa");

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