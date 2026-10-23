package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.CommentRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies the denormalised {@code products.rating_count} / {@code products.avg_stars}
 * contract: both values are recalculated in the same transaction as a comment create or
 * soft-delete, soft-deleted comments are excluded, and the stored average backs
 * {@link CommentService#getAvgStars(String)}.
 *
 * <p>{@link ProductCache} is mocked so this Postgres-only context never touches Redis; the
 * eviction contract is asserted with Mockito. Every test rolls back, so the shared Postgres
 * container is left untouched.
 */
@Tag("integration")
@Transactional
class CommentRatingIT extends AbstractIntegrationTest {

    private static final String AUTHOR = "joe";
    private static final String OTHER_AUTHOR = "jane";
    private static final String THIRD_AUTHOR = "bob";
    private static final String COMMENT_TITLE = "great";
    private static final String COMMENT_TEXT = "thorough review text";

    @MockitoBean
    ProductCache productCache;

    @Autowired
    CommentService commentService;

    @Autowired
    ProductService productService;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_storeCountAndAverage_when_commentsCreated() {
        authenticate();
        Product product = seedProduct("rating-two-comments");

        commentService.createComment(commentCommand(product, 5.0, AUTHOR));
        commentService.createComment(commentCommand(product, 3.0, OTHER_AUTHOR));
        productRepository.flush();

        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(2, 4.0));
        assertThat(commentService.getAvgStars(product.getName())).isEqualTo(4.0);

        ProductResponseDto response = productService.getProductByName(product.getName());
        assertThat(response.ratingCount()).isEqualTo(2);
        assertThat(response.avgStars()).isEqualTo(4.0);
    }

    @Test
    void should_recalculate_when_commentDeleted() {
        authenticate();
        Product product = seedProduct("rating-after-delete");
        CommentResponseDto fiveStars = commentService.createComment(
                commentCommand(product, 5.0, AUTHOR));
        commentService.createComment(commentCommand(product, 3.0, OTHER_AUTHOR));
        productRepository.flush();

        authenticate(fiveStars.author());
        commentService.deleteComment(fiveStars.id());
        productRepository.flush();

        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(1, 3.0));
        assertThat(commentService.getAvgStars(product.getName())).isEqualTo(3.0);
    }

    @Test
    void should_excludeSoftDeletedComment_when_statsAreRecalculated() {
        authenticate();
        Product product = seedProduct("rating-soft-deleted");
        commentService.createComment(commentCommand(product, 5.0, AUTHOR));
        CommentResponseDto threeStars = commentService.createComment(
                commentCommand(product, 3.0, OTHER_AUTHOR));
        CommentResponseDto oneStar = commentService.createComment(
                commentCommand(product, 1.0, THIRD_AUTHOR));
        productRepository.flush();

        // Bypass the service to prove the aggregate itself ignores the @SQLRestriction row.
        jdbcTemplate.update("UPDATE comments SET deleted_at = now() WHERE id = ?", oneStar.id());

        CommentRepository.RatingStats stats = commentRepository.findRatingStatsByProductId(product.getId());
        assertThat(stats.getRatingCount()).isEqualTo(2);
        assertThat(stats.getAverageStars()).isEqualTo(4.0);

        // A later mutation recalculates from live rows only: 5 remains after deleting 3.
        authenticate(threeStars.author());
        commentService.deleteComment(threeStars.id());
        productRepository.flush();

        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(1, 5.0));
    }

    @Test
    void should_keepZeroRating_when_productHasNoComments() {
        authenticate();
        Product product = seedProduct("rating-none");

        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(0, 0.0));
        assertThat(commentService.getAvgStars(product.getName())).isEqualTo(0.0);

        ProductResponseDto response = productService.getProductByName(product.getName());
        assertThat(response.ratingCount()).isZero();
        assertThat(response.avgStars()).isEqualTo(0.0);
    }

    @Test
    void should_resetRatingToZero_when_lastCommentDeleted() {
        authenticate();
        Product product = seedProduct("rating-last-deleted");
        CommentResponseDto onlyComment = commentService.createComment(
                commentCommand(product, 4.0, AUTHOR));
        productRepository.flush();

        authenticate(onlyComment.author());
        commentService.deleteComment(onlyComment.id());
        productRepository.flush();

        // The aggregate returns count 0 with a null average; the service stores 0.0.
        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(0, 0.0));
        assertThat(commentService.getAvgStars(product.getName())).isEqualTo(0.0);
    }

    @Test
    void should_evictProductCache_when_ratingChanges() {
        authenticate();
        Product product = seedProduct("rating-cache");

        CommentResponseDto comment = commentService.createComment(
                commentCommand(product, 5.0, AUTHOR));

        verify(productCache).evict(product.getId());

        authenticate(comment.author());
        commentService.deleteComment(comment.id());

        verify(productCache, times(2)).evict(product.getId());
    }

    private static void authenticate() {
        authenticate(AUTHOR);
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(new SimpleGrantedAuthority("SCOPE_catalog.read"),
                                new SimpleGrantedAuthority("SCOPE_catalog.review"))));
    }

    private Product seedProduct(String namePrefix) {
        Category category = new Category();
        category.setName("cat-" + UUID.randomUUID());
        categoryRepository.saveAndFlush(category);

        Product product = new Product();
        product.setName(namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        product.setDescription("Product seeded for the rating integration test");
        product.setPrice(new BigDecimal("9.99"));
        product.setInStock(true);
        product.setCharacteristics("seeded characteristics");
        product.setCategory(category);
        return productRepository.saveAndFlush(product);
    }

    private static CreateCommentCommand commentCommand(Product product, double star, String author) {
        return new CreateCommentCommand(COMMENT_TITLE, COMMENT_TEXT, star, author, product.getName());
    }

    private Rating ratingOf(UUID productId) {
        return jdbcTemplate.queryForObject(
                "select rating_count, avg_stars from products where id = ?",
                (rs, rowNum) -> new Rating(rs.getInt("rating_count"), rs.getDouble("avg_stars")),
                productId);
    }

    private record Rating(int count, double average) {
    }
}
