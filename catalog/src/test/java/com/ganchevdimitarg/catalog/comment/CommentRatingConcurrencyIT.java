package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Proves the pessimistic lock on the product row keeps the denormalised rating exact when
 * comment mutations overlap. The base class is Postgres-only; {@link ProductCache} is mocked
 * so no Redis is needed, mirroring {@link CommentRatingIT}.
 *
 * <p>The class is deliberately not {@code @Transactional}: every worker must commit so the
 * next worker's aggregate query can see its comment. Rows are removed in {@link #cleanUp()}
 * because the singleton Postgres container is shared across IT classes.
 */
@Tag("integration")
class CommentRatingConcurrencyIT extends AbstractIntegrationTest {

    private static final String AUTHOR = "joe";
    private static final String COMMENT_TITLE = "great";
    private static final String COMMENT_TEXT = "thorough review text";
    private static final int CONCURRENT_WRITERS = 20;
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

    private final List<UUID> productIds = new ArrayList<>();
    private UUID categoryId;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        productIds.forEach(productId -> {
            jdbcTemplate.update("DELETE FROM comments WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        });
        if (categoryId != null) {
            jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
        }
        productIds.clear();
        categoryId = null;
    }

    @Test
    void should_storeExactRating_when_commentsAreCreatedConcurrently() throws Exception {
        Product product = seedProduct("concurrent-create");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_WRITERS; i++) {
            // Four comments per star value: the exact mean is 3.0.
            double star = (i % 5) + 1;
            String author = "joe-" + i;
            tasks.add(() -> {
                authenticate();
                try {
                    commentService.createComment(commentCommand(product, star, author));
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            });
        }

        runConcurrently(tasks);

        assertThat(ratingOf(product.getId()))
                .isEqualTo(new Rating(CONCURRENT_WRITERS, 3.0));
        assertThat(liveCommentCount(product.getId())).isEqualTo(CONCURRENT_WRITERS);
    }

    @Test
    void should_storeExactRating_when_commentsAreDeletedConcurrently() throws Exception {
        Product product = seedProduct("concurrent-delete");
        authenticate();
        List<CommentResponseDto> comments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double star = i % 2 == 0 ? 1.0 : 5.0;
            comments.add(commentService.createComment(commentCommand(product, star, "joe-" + i)));
        }
        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(10, 3.0));

        List<Callable<Void>> tasks = comments.stream()
                .filter(comment -> comment.star() == 1.0)
                .map(comment -> (Callable<Void>) () -> {
                    authenticate(comment.author());
                    try {
                        commentService.deleteComment(comment.id());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    return null;
                })
                .toList();

        runConcurrently(tasks);

        // Only the five five-star comments remain.
        assertThat(ratingOf(product.getId())).isEqualTo(new Rating(5, 5.0));
        assertThat(liveCommentCount(product.getId())).isEqualTo(5);
    }

    /**
     * Submits every task, waits until all are parked at the start barrier, then releases
     * them together so the mutations genuinely overlap.
     */
    private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private Product seedProduct(String namePrefix) {
        Category category = new Category();
        category.setName("concurrency-" + UUID.randomUUID());
        categoryId = categoryRepository.saveAndFlush(category).getId();

        Product product = new Product();
        product.setName(namePrefix + "-" + UUID.randomUUID());
        product.setDescription("Product seeded for the rating concurrency test");
        product.setPrice(new BigDecimal("9.99"));
        product.setInStock(true);
        product.setCharacteristics("seeded characteristics");
        product.setCategory(category);
        Product saved = productRepository.saveAndFlush(product);
        productIds.add(saved.getId());
        return saved;
    }

    private static CreateCommentCommand commentCommand(Product product, double star, String author) {
        return new CreateCommentCommand(COMMENT_TITLE, COMMENT_TEXT, star, author, product.getName());
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

    private Rating ratingOf(UUID productId) {
        return jdbcTemplate.queryForObject(
                "select rating_count, avg_stars from products where id = ?",
                (rs, rowNum) -> new Rating(rs.getInt("rating_count"), rs.getDouble("avg_stars")),
                productId);
    }

    private int liveCommentCount(UUID productId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from comments where product_id = ? and deleted_at is null",
                Integer.class, productId);
        return count == null ? 0 : count;
    }

    private record Rating(int count, double average) {
    }
}
