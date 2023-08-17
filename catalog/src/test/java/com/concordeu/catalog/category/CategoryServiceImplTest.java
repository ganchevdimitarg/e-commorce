package com.concordeu.catalog.category;

import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Product;
import com.concordeu.catalog.mapper.CategoryMapper;
import com.concordeu.catalog.repositories.CategoryRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.service.category.CategoryService;
import com.concordeu.catalog.service.category.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private CategoryService testService;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryMapper mapper;

    String categoryName;
    CategoryDTO categoryDto;

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryRepository, productRepository, mapper);
        categoryName = "bbbbb";
        categoryDto = CategoryDTO.builder()
                .id(UUID.randomUUID())
                .name(categoryName)
                .build();
    }

    /*@Test
    void createCategoryShouldCreateCategoryIfNameIsNotEmpty() {

        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(categoryDto);

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(argumentCaptor.capture());

        Category category = argumentCaptor.getValue();
        assertThat(category).isNotNull();
        assertThat(category.getName()).isEqualTo(categoryName);
    }

    @Test
    void createCategoryShouldThrowExceptionIfNameIsEmpty() {
        assertThatThrownBy(() -> testService.createCategory(CategoryDTO.builder().name("").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category name is empty: ");

        verify(categoryRepository, never()).saveAndFlush(any());
    }*/

    @Test
    void createCategoryShouldThrowExceptionIfCategoryExist() {
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.createCategory(categoryDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category with the name: " + categoryName + " already exist.");

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteCategoryShouldDeleteProductIfProductExist() {
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        testService.deleteCategory(categoryName);

        verify(categoryRepository).deleteByName(categoryName);
    }

    @Test
    void deleteCategoryShouldDeleteIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.deleteCategory(categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: bbbbb");

        verify(categoryRepository, never()).deleteByName(any());
    }

    @Test
    void moveOneProductShouldMoveProductFromOneCategoryToAnotherCategory() {
        Category categoryFrom = Category.builder()
                .id(UUID.randomUUID())
                .name("pc")
                .products(Set.of(Product.builder().name("mouse").build()))
                .build();

        Category categoryTo = Category.builder()
                .id(UUID.randomUUID())
                .name("acc")
                .build();

        when(categoryRepository.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryRepository.getById(categoryFrom.getId())).thenReturn(categoryFrom);

        when(mapper.mapCategoryToCategoryDTO(categoryFrom)).thenReturn(CategoryDTO.builder().id(categoryFrom.getId()).build());
        when(mapper.mapCategoryToCategoryDTO(categoryTo)).thenReturn(CategoryDTO.builder().id(categoryTo.getId()).build());

        testService.moveOneProduct(categoryFrom.getName(), categoryTo.getName(), "mouse");

        verify(productRepository).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfProductDoesNotExist() {
        Category categoryFrom = Category.builder().id(UUID.randomUUID()).name("pc").products(Set.of(Product.builder().name("").build())).build();
        Category categoryTo = Category.builder().id(UUID.randomUUID()).name("acc").build();

        when(categoryRepository.findByName(categoryFrom.getName())).thenReturn(Optional.of(categoryFrom));
        when(categoryRepository.findByName(categoryTo.getName())).thenReturn(Optional.of(categoryTo));

        when(categoryRepository.getById(categoryFrom.getId())).thenReturn(categoryFrom);

        when(mapper.mapCategoryToCategoryDTO(categoryFrom)).thenReturn(CategoryDTO.builder().id(categoryFrom.getId()).build());
        when(mapper.mapCategoryToCategoryDTO(categoryTo)).thenReturn(CategoryDTO.builder().id(categoryTo.getId()).build());

        assertThatThrownBy(() -> testService.moveOneProduct(categoryFrom.getName(), categoryTo.getName(), "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: mouse");

        verify(productRepository, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfFirstCategoryNameIsEmpty() {
        assertThatThrownBy(() -> testService.moveOneProduct("", "acc", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: ");

        verify(productRepository, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfSecondCategoryNameIsEmpty() {
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: ");

        verify(productRepository, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfFirstCategoryDoesNotExist() {
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

        verify(productRepository, never()).changeCategory(any(), any());
    }

    @Test
    void moveOneProductShouldThrowExceptionIfSecondCategoryDoesNotExist() {
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));
        assertThatThrownBy(() -> testService.moveOneProduct(categoryName, "aaaaa", "mouse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: aaaaa");

        verify(productRepository, never()).changeCategory(any(), any());
    }

    @Test
    void getCategoriesShouldReturnAllCategories() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Category> products = Arrays.asList(new Category(), new Category());
        Page<Category> page = new PageImpl<>(products, pageRequest, products.size());
        when(categoryRepository.findAll(pageRequest)).thenReturn(page);

        testService.getCategoriesByPage(1, 5);
        verify(categoryRepository).findAll(pageRequest);
    }
}