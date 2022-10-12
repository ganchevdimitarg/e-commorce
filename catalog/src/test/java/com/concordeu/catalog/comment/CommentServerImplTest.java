package com.concordeu.catalog.comment;

import com.concordeu.catalog.ModelMapper;
import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CommentServerImplTest {

    CommentService testService;

    @Mock
    CommentRepository commentRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    CommentDataValidator validator;
    @Mock
    ModelMapper modelMapper;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentRepository, productRepository, validator, modelMapper);
    }

    @Test
    void createCommentShouldCreateComment() {
        CommentDto commentDto = CommentDto.builder().build();
        when(validator.validateData(commentDto)).thenReturn(true);

        String productName = "aaa";
        Product product = Product.builder().name(productName).build();
        when(productRepository.findByName(productName)).thenReturn(Optional.of(product));

        Comment comment = Comment.builder().build();
        when(modelMapper.mapDtoToComment(commentDto)).thenReturn(comment);

        testService.createComment(commentDto, productName);

        ArgumentCaptor<Comment> argument = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(argument.capture());

        Comment captureComment = argument.getValue();
        assertThat(captureComment).isNotNull();
        assertThat(captureComment).isEqualTo(comment);
    }

    @Test
    void createCommentShouldThrowExceptionIfProductDoesNotExist() {
        CommentDto commentDto = CommentDto.builder().build();
        when(validator.validateData(commentDto)).thenReturn(true);

        String productName = "aaa";
        assertThatThrownBy(() -> testService.createComment(commentDto, productName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: " + productName);

        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void findAllByProductNameShouldReturnAllCommentsByProduct() {
        Product product = Product.builder().build();
        when(productRepository.findByName(any(String.class))).thenReturn(Optional.of(product));

        testService.findAllByProductName("aaaaa");

        verify(commentRepository).findAllByProduct(product);
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductNameIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByProductName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProduct(any());
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.findAllByProductName("aaaaa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProduct(any());
    }

    @Test
    void findAllByAuthorShouldReturnAllCommentsByAuthor() {
        testService.findAllByAuthor("aaaaa");

        verify(commentRepository).findAllByAuthor(any());
    }

    @Test
    void findAllByAuthorShouldThrowExceptionIfAuthorIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByAuthor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such author: ");

        verify(commentRepository, never()).findAllByAuthor(any());
    }
}