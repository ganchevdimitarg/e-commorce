package com.concordeu.catalog.category;

import com.concordeu.catalog.ModelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private CategoryService testService;

    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ModelMapper modelMapper;

    @BeforeEach
    void setUp() {
        testService = new CategoryServiceImpl(categoryRepository, modelMapper);
    }

    @Test
    void createCategoryShouldCreateCategoryIfNameIsNotEmpty() {
        String categoryName = "PC";

        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.empty());

        testService.createCategory(categoryName);

        ArgumentCaptor<Category> argumentCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(argumentCaptor.capture());

        Category category = argumentCaptor.getValue();
        assertThat(category).isNotNull();
        assertThat(category.getName()).isEqualTo(categoryName);
    }

    @Test
    void createCategoryShouldThrowExceptionIfNameIsEmpty() {
        assertThatThrownBy(() -> testService.createCategory(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: ");

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCategoryShouldThrowExceptionIfCategoryExist() {
        String categoryName = "PC";
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.createCategory(categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category with the name: " + categoryName + " already exists.");

        verify(categoryRepository, never()).saveAndFlush(any());
    }
}