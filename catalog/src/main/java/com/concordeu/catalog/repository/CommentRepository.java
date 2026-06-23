package com.concordeu.catalog.repository;

import com.concordeu.catalog.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {
    @Query(value = """
            SELECT * FROM comments WHERE PRODUCT_ID = ?1 AND deleted_at IS NULL
            """, nativeQuery = true)
    Page<Comment> findAllByProductIdByPage(String productId, Pageable pageable);

    @Query(value = """
            SELECT * FROM comments WHERE AUTHOR = ?1 AND deleted_at IS NULL
            """, nativeQuery = true)
    Page<Comment> findAllByAuthorByPage(String author, Pageable pageable);

    @Query(value = """
            SELECT c.* FROM comments c
            JOIN products p ON c.product_id = p.id
            WHERE p.name = ?1 AND c.deleted_at IS NULL AND p.deleted_at IS NULL
            """, nativeQuery = true)
    List<Comment> findAllByProductName(String productName);
}
