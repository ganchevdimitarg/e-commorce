package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.repository.CommentRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Comment;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import com.ganchevdimitarg.catalog.service.comment.CommentServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("0030223b-fdb9-40e2-a4b0-81bdf54479a2");

    CommentService testService;

    @Mock
    CommentRepository commentRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    MapStructMapper mapStructMapper;
    @Mock
    ProductCache productCacheEvictor;

    @BeforeEach
    void setUp() {
        testService = new CommentServiceImpl(commentRepository, productRepository, mapStructMapper,
                new SimpleMeterRegistry(), productCacheEvictor);
        authenticate("joe", "SCOPE_catalog.review");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_createComment_when_productExists() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        when(productRepository.findByNameForUpdate("mouse")).thenReturn(Optional.of(product));
        CommentRepository.RatingStats stats = ratingStats(2, 4.0);
        when(commentRepository.findRatingStatsByProductId(PRODUCT_ID)).thenReturn(stats);
        Comment comment = new Comment();
        comment.setTitle("nice");
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(comment);
        when(mapStructMapper.mapCommentToCommentResponseDto(comment))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "nice", "great product!!", 5.0,
                        "joe", PRODUCT_ID, "mouse"));

        CommentResponseDto result = testService.createComment(
                new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse"));

        verify(commentRepository).saveAndFlush(comment);
        verify(productCacheEvictor).evict(PRODUCT_ID);
        assertThat(product.getRatingCount()).isEqualTo(2);
        assertThat(product.getAvgStars()).isEqualTo(4.0);
        assertThat(result.title()).isEqualTo("nice");
    }

    @Test
    void should_resetRatingToZero_when_ratingStatsAreMissingAfterCreate() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        product.setRatingCount(7);
        product.setAvgStars(4.5);
        when(productRepository.findByNameForUpdate("mouse")).thenReturn(Optional.of(product));
        when(commentRepository.findRatingStatsByProductId(PRODUCT_ID)).thenReturn(null);
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(new Comment());
        when(mapStructMapper.mapCommentToCommentResponseDto(any(Comment.class)))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "nice", "great product!!", 5.0,
                        "joe", PRODUCT_ID, "mouse"));

        testService.createComment(new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse"));

        assertThat(product.getRatingCount()).isZero();
        assertThat(product.getAvgStars()).isEqualTo(0.0);
    }

    @Test
    void should_setZeroAverage_when_ratingStatsAverageIsNullAfterCreate() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        when(productRepository.findByNameForUpdate("mouse")).thenReturn(Optional.of(product));
        CommentRepository.RatingStats stats = ratingStats(1, null);
        when(commentRepository.findRatingStatsByProductId(PRODUCT_ID)).thenReturn(stats);
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(new Comment());
        when(mapStructMapper.mapCommentToCommentResponseDto(any(Comment.class)))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "nice", "great product!!", 5.0,
                        "joe", PRODUCT_ID, "mouse"));

        testService.createComment(new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse"));

        assertThat(product.getRatingCount()).isEqualTo(1);
        assertThat(product.getAvgStars()).isEqualTo(0.0);
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
    void should_throwConflict_when_authorAlreadyReviewedProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        when(productRepository.findByNameForUpdate("mouse")).thenReturn(Optional.of(product));
        when(commentRepository.existsByProduct_IdAndAuthor(PRODUCT_ID, "joe")).thenReturn(true);

        assertThatThrownBy(() -> testService.createComment(
                new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse")))
                .isInstanceOf(ConflictException.class);

        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_returnComments_when_productExists() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> comments = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(comments, pageRequest, comments.size());
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("aaaa89");

        when(productRepository.findByName(any(String.class))).thenReturn(Optional.of(product));
        when(commentRepository.findAllByProductIdByPage(PRODUCT_ID, pageRequest)).thenReturn(page);
        when(mapStructMapper.mapCommentToCommentResponseDto(any(Comment.class)))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "nice", "great product!!", 5.0,
                        "joe", PRODUCT_ID, "aaaa89"));

        Page<CommentResponseDto> result = testService.findAllByProductNameByPage("aaaa89", pageRequest);

        verify(commentRepository).findAllByProductIdByPage(product.getId(), pageRequest);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void should_throwNotFound_when_findAllByProductNameForNonExistentProduct() {
        assertThatThrownBy(() -> testService.findAllByProductNameByPage("aaaaa", PageRequest.of(1, 5)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: aaaaa");

        verify(commentRepository, never()).findAllByProductIdByPage(any(UUID.class), any(PageRequest.class));
    }

    @Test
    void should_returnComments_when_authorExists() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Comment> comments = Arrays.asList(new Comment(), new Comment());
        Page<Comment> page = new PageImpl<>(comments, pageRequest, comments.size());
        String author = "aaaa";

        when(commentRepository.findAllByAuthorByPage(author, pageRequest)).thenReturn(page);
        when(mapStructMapper.mapCommentToCommentResponseDto(any(Comment.class)))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "nice", "great product!!", 5.0,
                        author, PRODUCT_ID, "mouse"));

        Page<CommentResponseDto> result = testService.findAllByAuthorByPage(author, pageRequest);

        verify(commentRepository).findAllByAuthorByPage(any(String.class), any(PageRequest.class));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void should_returnStoredAverageStars_when_productExists() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        product.setAvgStars(4.0);
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));

        double avgStars = testService.getAvgStars("mouse");

        assertThat(avgStars).isEqualTo(4.0);
    }

    @Test
    void should_returnStoredZero_when_productHasNoRating() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("mouse");
        product.setAvgStars(0.0);
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));

        double avgStars = testService.getAvgStars("mouse");

        assertThat(avgStars).isEqualTo(0.0);
    }

    @Test
    void should_returnZero_when_productDoesNotExist() {
        when(productRepository.findByName("nonexistent")).thenReturn(Optional.empty());

        double avgStars = testService.getAvgStars("nonexistent");

        assertThat(avgStars).isEqualTo(0.0);
    }

    @Test
    void should_incrementCreatedCounter_when_commentCreated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommentServiceImpl service =
                new CommentServiceImpl(commentRepository, productRepository, mapStructMapper, registry,
                        productCacheEvictor);

        Product product = new Product();
        product.setName("aaa");
        when(productRepository.findByNameForUpdate("aaa")).thenReturn(Optional.of(product));

        Comment comment = new Comment();
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(comment);
        when(mapStructMapper.mapCommentToCommentResponseDto(comment))
                .thenReturn(new CommentResponseDto(UUID.randomUUID(), "", "", 0, "", null, null));

        service.createComment(new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "aaa"));

        assertThat(registry.get("catalog.comment.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void should_deleteComment_when_requesterIsAuthor() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor("joe");
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        testService.deleteComment(commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void should_deleteComment_when_requesterIsAdmin() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor("joe");
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        authenticate("admin-1", "SCOPE_catalog.review", "ROLE_ADMIN");

        testService.deleteComment(commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void should_throwAccessDenied_when_requesterIsNeitherAuthorNorAdmin() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor("joe");
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        authenticate("mallory", "SCOPE_catalog.review");

        assertThatThrownBy(() -> testService.deleteComment(commentId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the author or an administrator may delete this comment");

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void should_throwAccessDenied_when_authorIsNullAndRequesterIsNotAdmin() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor(null);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        authenticate("mallory", "SCOPE_catalog.review");

        assertThatThrownBy(() -> testService.deleteComment(commentId))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void should_throwAccessDenied_when_noAuthenticationIsPresent() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor("joe");
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> testService.deleteComment(commentId))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void should_throwNotFound_when_commentDoesNotExist() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.deleteComment(commentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Comment not found: " + commentId);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void should_evictProductCache_when_commentDeleted() {
        UUID commentId = UUID.randomUUID();
        Product product = new Product();
        product.setId(PRODUCT_ID);
        Comment comment = commentWithAuthor("joe");
        comment.setProduct(product);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        testService.deleteComment(commentId);

        verify(productCacheEvictor).evict(PRODUCT_ID);
    }

    @Test
    void should_recalculateRating_when_commentDeleted() {
        UUID commentId = UUID.randomUUID();
        Product product = new Product();
        product.setId(PRODUCT_ID);
        Comment comment = commentWithAuthor("joe");
        comment.setProduct(product);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        CommentRepository.RatingStats stats = ratingStats(1, 3.0);
        when(commentRepository.findRatingStatsByProductId(PRODUCT_ID)).thenReturn(stats);

        testService.deleteComment(commentId);

        // flush() runs the soft-delete SQL before the aggregate query sees the new state
        verify(commentRepository).flush();
        verify(productRepository).findByIdForUpdate(PRODUCT_ID);
        assertThat(product.getRatingCount()).isEqualTo(1);
        assertThat(product.getAvgStars()).isEqualTo(3.0);
    }

    @Test
    void should_deleteComment_when_productIsSoftDeleted() {
        UUID commentId = UUID.randomUUID();
        Comment comment = commentWithAuthor("joe");
        comment.setProduct(null);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        testService.deleteComment(commentId);

        verify(commentRepository).delete(comment);
        verify(productCacheEvictor, never()).evict(any());
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void should_incrementDeletedCounter_when_commentDeleted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommentServiceImpl service =
                new CommentServiceImpl(commentRepository, productRepository, mapStructMapper, registry,
                        productCacheEvictor);
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(commentWithAuthor("joe")));

        service.deleteComment(commentId);

        assertThat(registry.get("catalog.comment.deleted").counter().count()).isEqualTo(1.0);
    }

    private static void authenticate(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static Comment commentWithAuthor(String author) {
        Comment comment = new Comment();
        comment.setTitle("nice");
        comment.setText("great product!!");
        comment.setStar(5.0);
        comment.setAuthor(author);
        return comment;
    }

    private static CommentRepository.RatingStats ratingStats(long count, Double average) {
        CommentRepository.RatingStats stats = mock(CommentRepository.RatingStats.class);
        when(stats.getRatingCount()).thenReturn(count);
        when(stats.getAverageStars()).thenReturn(average);
        return stats;
    }
}
