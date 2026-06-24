package com.concordeu.catalog.comment;

import com.concordeu.catalog.repository.CommentRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.dto.comment.CreateCommentCommand;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.service.comment.CommentServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        testService = new CommentServiceImpl(commentRepository, productRepository, mapStructMapper, new SimpleMeterRegistry());
    }

    @Test
    void should_createComment_when_productExists() {
        Product product = new Product();
        product.setName("mouse");
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));
        Comment comment = new Comment();
        comment.setTitle("nice");
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(comment);
        when(mapStructMapper.mapCommentToCommentResponseDto(comment))
                .thenReturn(new CommentResponseDto("nice", "great product!!", 5.0, "joe", null));

        CommentResponseDto result = testService.createComment(
                new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse"));

        verify(commentRepository).saveAndFlush(comment);
        assertThat(result.title()).isEqualTo("nice");
    }

    @Test
    void should_throwNotFound_when_createCommentForNonExistentProduct() {
        String productName = "aaa";
        assertThatThrownBy(() -> testService.createComment(
                new CreateCommentCommand("nice", "great product!!", 5.0, "joe", productName)))
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

        testService.findAllByProductNameByPage("aaaa89", pageRequest);

        verify(commentRepository).findAllByProductIdByPage(product.getId(), pageRequest);
    }

    @Test
    void should_throwNotFound_when_findAllByProductNameForNonExistentProduct() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa", PageRequest.of(1, 5)))
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

        testService.findAllByAuthorByPage(productName, pageRequest);

        verify(commentRepository).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
    }

    @Test
    void should_returnZero_when_noCommentsExist() {
        when(commentRepository.findAllByProductName("empty")).thenReturn(List.of());

        double avgStars = testService.getAvgStars("empty");

        assertThat(avgStars).isEqualTo(0.0);
    }

    @Test
    void should_returnAverageStars_when_commentsExist() {
        Comment comment1 = new Comment();
        comment1.setStar(4);
        Comment comment2 = new Comment();
        comment2.setStar(5);
        Comment comment3 = new Comment();
        comment3.setStar(3);

        when(commentRepository.findAllByProductName("mouse")).thenReturn(List.of(comment1, comment2, comment3));

        double avgStars = testService.getAvgStars("mouse");

        assertThat(avgStars).isEqualTo(4.0);
    }

    @Test
    void should_incrementCreatedCounter_when_commentCreated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommentServiceImpl service =
                new CommentServiceImpl(commentRepository, productRepository, mapStructMapper, registry);

        Product product = new Product();
        product.setName("aaa");
        when(productRepository.findByName("aaa")).thenReturn(Optional.of(product));

        Comment comment = new Comment();
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(comment);
        when(mapStructMapper.mapCommentToCommentResponseDto(comment))
                .thenReturn(new CommentResponseDto("", "", 0, "", null));

        service.createComment(new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "aaa"));

        assertThat(registry.get("catalog.comment.created").counter().count()).isEqualTo(1.0);
    }
}
