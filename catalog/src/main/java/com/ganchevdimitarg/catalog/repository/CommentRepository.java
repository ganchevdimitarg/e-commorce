package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    @Query(value = """
            SELECT * FROM comments WHERE PRODUCT_ID = ?1 AND deleted_at IS NULL
            """, nativeQuery = true)
    Page<Comment> findAllByProductIdByPage(UUID productId, Pageable pageable);

    @Query(value = """
            SELECT * FROM comments WHERE AUTHOR = ?1 AND deleted_at IS NULL
            """, nativeQuery = true)
    Page<Comment> findAllByAuthorByPage(String author, Pageable pageable);

    @Query("select avg(c.star) from Comment c where c.product.id = :productId")
    Double findAverageStarByProductId(@Param("productId") UUID productId);
}
