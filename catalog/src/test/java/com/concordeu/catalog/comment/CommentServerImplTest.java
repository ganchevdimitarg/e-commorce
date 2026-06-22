package com.concordeu.catalog.comment;

import com.concordeu.catalog.repository.CommentRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.exception.ValidationException;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.service.comment.CommentServiceImpl;
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
    MapStructMapper mapStructMapper;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentRepository, productRepository, mapStructMapper);
    }

    @Test
    void should_createComment_when_productExists() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("", "", 0, "", null);

        String productName = "aaa";
        Product product = new Product();
        product.setName(productName);
        when(productRepository.findByName(productName)).thenReturn(Optional.of(product));

        Comment comment = new Comment();
        when(mapStructMapper.mapCommentResponseDtoToComment(commentResponseDto)).thenReturn(comment);

        testService.createComment(commentResponseDto, productName);

        ArgumentCaptor<Comment> argument = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(argument.capture());

        Comment captureComment = argument.getValue();
        assertThat(captureComment).isNotNull();
        assertThat(captureComment).isEqualTo(comment);
    }

    @Test
    void should_throwNotFound_when_createCommentForNonExistentProduct() {
        CommentResponseDto commentResponseDto = new CommentResponseDto("", "", 0, "", null);

        String productName = "aaa";
        assertThatThrownBy(() -> testService.createComment(commentResponseDto, productName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + productName);

        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_returnComments_when_productExists() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        String productId = "0030223b-fdb9-40e2-a4b0-81bdf54479a2";
        Product product = new Product();
        product.setId(productId);
        product.setName("aaaa89");

        when(productRepository.findByName(any(String.class))).thenReturn(Optional.of(product));
        when(commentRepository.findAllByProductIdByPage(productId, pageRequest)).thenReturn(page);

        testService.findAllByProductNameByPage("aaaa89", 1, 5);

        verify(commentRepository).findAllByProductIdByPage(product.getId(), pageRequest);
    }

    @Test
    void should_throwValidation_when_findAllByProductNameWithEmptyName() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("", 1, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Product name is empty");

        verify(commentRepository, never()).findAllByProductIdByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void should_throwNotFound_when_findAllByProductNameForNonExistentProduct() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa", 1, 5))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: aaaaa");

        verify(commentRepository, never()).findAllByProductIdByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void should_returnComments_when_authorExists() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> products = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(products, pageRequest, products.size());
        String productName = "aaaa";

        when(commentRepository.findAllByAuthorByPage(productName, pageRequest)).thenReturn(page);

        testService.findAllByAuthorByPage(productName, 1, 5);

        verify(commentRepository).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void should_throwValidation_when_findAllByAuthorWithEmptyAuthor() {
        assertThatThrownBy(() -> testService.findAllByAuthorByPage("", 1, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No such author: ");

        verify(commentRepository, never()).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }
}