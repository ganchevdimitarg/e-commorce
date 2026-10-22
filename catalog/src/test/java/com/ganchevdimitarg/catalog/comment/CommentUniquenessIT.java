package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the one-live-review-per-author-and-product rule: the service pre-check rejects a
 * duplicate review with a conflict, a soft-deleted review frees the slot, and the partial
 * unique index is the backstop when the pre-check is bypassed or raced.
 *
 * <p>Deliberately not {@code @Transactional}: every create and delete must commit so the
 * next request — and the other concurrent worker — can see it. {@link ProductCache} is
 * mocked so this Postgres-only context never touches Redis.
 */
@Tag("integration")
class CommentUniquenessIT extends AbstractIntegrationTest {

    private static final String COMMENT_TITLE = "great";
    private static final String COMMENT_TEXT = "thorough review text";
    private static final long TIMEOUT_SECONDS = 30;

    @MockitoBean
    ProductCache productCache;

    @Autowired
    CommentService commentService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID categoryId;
    private UUID productId;
    private String productName;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("uniqueness-" + UUID.randomUUID());
        categoryId = categoryRepository.saveAndFlush(category).getId();

        Product product = new Product();
        productName = "uniqueness-product-" + UUID.randomUUID();
        product.setName(productName);
        product.setDescription("Product seeded for the comment uniqueness test");
        product.setPrice(new BigDecimal("9.99"));
        product.setInStock(true);
        product.setCharacteristics("seeded characteristics");
        product.setCategory(category);
        productId = productRepository.saveAndFlush(product).getId();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM comments WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
    }

    @Test
    void should_rejectSecondReview_when_authorAlreadyReviewedProduct() {
        authenticate("alice");
        commentService.createComment(commentCommand(5.0, "alice"));

        assertThatThrownBy(() -> commentService.createComment(commentCommand(1.0, "alice")))
                .isInstanceOf(ConflictException.class);

        assertThat(liveCommentCount()).isEqualTo(1);
    }

    @Test
    void should_allowReviewAgain_whenPreviousReviewDeleted() {
        authenticate("alice");
        CommentResponseDto first = commentService.createComment(commentCommand(5.0, "alice"));

        commentService.deleteComment(first.id());

        CommentResponseDto second = commentService.createComment(commentCommand(4.0, "alice"));

        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isNotNull();
        assertThat(liveCommentCount()).isEqualTo(1);
    }

    @Test
    void should_allowOnlyOne_when_twoReviewsPostedConcurrently() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Object>> tasks = List.of(
                concurrentReviewAttempt(ready, start),
                concurrentReviewAttempt(ready, start));

        List<Object> outcomes = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }

        assertThat(outcomes).filteredOn(CommentResponseDto.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(liveCommentCount()).isEqualTo(1);
    }

    @Test
    void should_rejectDuplicate_whenServiceGuardIsBypassed() {
        insertLiveComment(UUID.randomUUID(), "alice");

        assertThatThrownBy(() -> insertLiveComment(UUID.randomUUID(), "alice"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Callable<Object> concurrentReviewAttempt(CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            authenticate("alice");
            try {
                return commentService.createComment(commentCommand(5.0, "alice"));
            } catch (ConflictException ex) {
                return ex;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void insertLiveComment(UUID commentId, String author) {
        jdbcTemplate.update("""
                        INSERT INTO comments (id, author, star, text, title, product_id,
                                              created_at, updated_at, version)
                        VALUES (?, ?, 5, 'This is a thorough review text', 'Great product', ?,
                                now(), now(), 0)
                        """,
                commentId, author, productId);
    }

    private CreateCommentCommand commentCommand(double star, String author) {
        return new CreateCommentCommand(COMMENT_TITLE, COMMENT_TEXT, star, author, productName);
    }

    private int liveCommentCount() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from comments where product_id = ? and deleted_at is null",
                Integer.class, productId);
        return count == null ? 0 : count;
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(new SimpleGrantedAuthority("SCOPE_catalog.read"),
                                new SimpleGrantedAuthority("SCOPE_catalog.review"))));
    }
}
