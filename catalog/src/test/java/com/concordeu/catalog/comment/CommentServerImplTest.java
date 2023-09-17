package com.concordeu.catalog.comment;

import com.concordeu.catalog.dto.CommentDTO;
import com.concordeu.catalog.mapper.CommentMapper;
import com.concordeu.catalog.repositories.CommentRepository;
import com.concordeu.catalog.repositories.ProductRepository;
import com.concordeu.catalog.entities.Comment;
import com.concordeu.catalog.entities.Product;
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
import java.util.UUID;

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
    CommentMapper mapper;

    CommentDTO commentDTO;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentRepository, productRepository, validator, mapper);
        commentDTO = CommentDTO.builder().build();
    }

    @Test
    void createCommentShouldCreateComment() {
        when(validator.validateData(commentDTO)).thenReturn(true);

        String productName = "aaa";
        Product product = Product.builder().name(productName).build();
        when(productRepository.findByName(productName)).thenReturn(Optional.of(product));

        Comment comment = Comment.builder().build();
        when(mapper.mapCommentDTOToComment(commentDTO)).thenReturn(comment);

        testService.createComment(commentDTO, productName);

        ArgumentCaptor<Comment> argument = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(argument.capture());

        Comment captureComment = argument.getValue();
        assertThat(captureComment).isNotNull();
        assertThat(captureComment).isEqualTo(comment);
    }

    @Test
    void createCommentShouldThrowExceptionIfProductDoesNotExist() {
        when(validator.validateData(commentDTO)).thenReturn(true);

        String productName = "aaa";
        assertThatThrownBy(() -> testService.createComment(commentDTO, productName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: " + productName);

        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void findAllByProductNameShouldReturnAllCommentsByProduct() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("aaaa89").build();

        when(productRepository.findByName(any(String.class))).thenReturn(Optional.of(product));
        when(commentRepository.findAllByProductIdByPage(productId, pageRequest)).thenReturn(page);

        testService.findAllByProductNameByPage("aaaa89", 1, 5);

        verify(commentRepository).findAllByProductIdByPage(product.getId(), pageRequest);
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductNameIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProductIdByPage(any(UUID.class), any(PageRequest.class));
    }

    @Test
    void findAllByProductNameShouldThrowExceptionIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such product: ");

        verify(commentRepository, never()).findAllByProductIdByPage(any(UUID.class), any(PageRequest.class));
    }

    @Test
    void findAllByAuthorShouldReturnAllCommentsByAuthor() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        String productName = "aaaa";

        when(commentRepository.findAllByAuthorByPage(productName, pageRequest)).thenReturn(page);

        testService.findAllByAuthorByPage(productName, 1, 5);

        verify(commentRepository).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void findAllByAuthorShouldThrowExceptionIfAuthorIsEmpty() {
        assertThatThrownBy(() -> testService.findAllByAuthorByPage("", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such author: ");

        verify(commentRepository, never()).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }
}