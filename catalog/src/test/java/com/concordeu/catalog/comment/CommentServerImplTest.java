package com.concordeu.catalog.comment;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dao.CommentRepository;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductRepository;
import com.concordeu.catalog.dto.CommentDto;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.service.comment.CommentServiceImpl;
import com.concordeu.catalog.validator.CommentDataValidator;
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
    MapStructMapper mapStructMapper;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentRepository, productRepository, validator, mapStructMapper);
    }

    @Test
    void createCommentShouldCreateComment() {
        CommentDto commentDto = CommentDto.builder().build();
        when(validator.validateData(commentDto)).thenReturn(true);

        String productName = "aaa";
        Product product = Product.builder().name(productName).build();
        when(productRepository.findByName(productName)).thenReturn(Optional.of(product));

        Comment comment = Comment.builder().build();
        when(mapStructMapper.mapDtoToComment(commentDto)).thenReturn(comment);

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
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        Product product = Product.builder().id("0030223b-fdb9-40e2-a4b0-81bdf54479a2").name("aaaa89").build();
        when(productRepository.findByName(any(String.class))).thenReturn(Optional.of(product));

        testService.findAllByProductNameByPage("aaaa89", 1 , 5);

        verify(commentRepository).findAllByProductIdByPage(product.getId(), pageRequest);
    }
//TODO NOT WORK
    /*@Test
    void findAllByProductNameShouldThrowExceptionIfProductNameIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProduct(any());
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProduct(any());
    }

    @Test
    void findAllByAuthorShouldReturnAllCommentsByAuthor() {
        testService.findAllByAuthorByPage("aaaaa");

        verify(commentRepository).findAllByAuthorByPage(any());
    }

    @Test
    void findAllByAuthorShouldThrowExceptionIfAuthorIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByAuthorByPage(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such author: ");

        verify(commentRepository, never()).findAllByAuthorByPage(any());
    }*/
}