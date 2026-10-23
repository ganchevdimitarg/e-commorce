package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    @EntityGraph(attributePaths = "product")
    @Query(value = "select c from Comment c where c.product.id = :productId",
           countQuery = "select count(c) from Comment c where c.product.id = :productId")
    Page<Comment> findAllByProductIdByPage(@Param("productId") UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    @Query(value = "select c from Comment c where c.author = :author",
           countQuery = "select count(c) from Comment c where c.author = :author")
    Page<Comment> findAllByAuthorByPage(@Param("author") String author, Pageable pageable);

    /**
     * {@code @SQLRestriction} hides soft-deleted comments, so a deleted review does not
     * occupy the author's slot for the product and can be posted again.
     */
    boolean existsByProduct_IdAndAuthor(UUID productId, String author);

    /**
     * Aggregate backing the denormalised rating on {@code products}. The entity's
     * {@code @SQLRestriction} excludes soft-deleted comments from both values.
     */
    interface RatingStats {
        long getRatingCount();

        Double getAverageStars();
    }

    @Query("select count(c) as ratingCount, avg(c.star) as averageStars " +
           "from Comment c where c.product.id = :productId")
    RatingStats findRatingStatsByProductId(@Param("productId") UUID productId);
}
