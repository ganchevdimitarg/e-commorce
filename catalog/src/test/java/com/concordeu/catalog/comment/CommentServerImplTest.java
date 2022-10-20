package com.concordeu.catalog.comment;

import com.concordeu.client.catalog.comment.CommentResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.dao.CommentDao;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dao.ProductDao;
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
    CommentDao commentDao;
    @Mock
    ProductDao productDao;
    @Mock
    CommentDataValidator validator;
    @Mock
    MapStructMapper mapStructMapper;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentDao, productDao, validator, mapStructMapper);
    }

    @Test
    void createCommentShouldCreateComment() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("", "", 0, "");
        when(validator.validateData(commentResponseDto)).thenReturn(true);

        String productName = "aaa";
        Product product = Product.builder().name(productName).build();
        when(productDao.findByName(productName)).thenReturn(Optional.of(product));

        Comment comment = Comment.builder().build();
        when(mapStructMapper.mapCommentResponseDtoToComment(commentResponseDto)).thenReturn(comment);

        testService.createComment(commentResponseDto, productName);

        ArgumentCaptor<Comment> argument = ArgumentCaptor.forClass(Comment.class);
        verify(commentDao).saveAndFlush(argument.capture());

        Comment captureComment = argument.getValue();
        assertThat(captureComment).isNotNull();
        assertThat(captureComment).isEqualTo(comment);
    }

    @Test
    void createCommentShouldThrowExceptionIfProductDoesNotExist() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("", "", 0, "");
        when(validator.validateData(commentResponseDto)).thenReturn(true);

        String productName = "aaa";
        assertThatThrownBy(() -> testService.createComment(commentResponseDto, productName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: " + productName);

        verify(commentDao, never()).saveAndFlush(any());
    }

    @Test
    void findAllByProductNameShouldReturnAllCommentsByProduct() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        String productId = "0030223b-fdb9-40e2-a4b0-81bdf54479a2";
        Product product = Product.builder().id(productId).name("aaaa89").build();

        when(productDao.findByName(any(String.class))).thenReturn(Optional.of(product));
        when(commentDao.findAllByProductIdByPage(productId, pageRequest)).thenReturn(page);

        testService.findAllByProductNameByPage("aaaa89", 1, 5);

        verify(commentDao).findAllByProductIdByPage(product.getId(), pageRequest);
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductNameIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentDao, never()).findAllByProductIdByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentDao, never()).findAllByProductIdByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void findAllByAuthorShouldReturnAllCommentsByAuthor() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        String productName = "aaaa";

        when(commentDao.findAllByAuthorByPage(productName, pageRequest)).thenReturn(page);

        testService.findAllByAuthorByPage(productName, 1, 5);

        verify(commentDao).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void findAllByAuthorShouldThrowExceptionIfAuthorIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByAuthorByPage("", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such author: ");

        verify(commentDao, never()).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }
}