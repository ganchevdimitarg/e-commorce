package com.concordeu.catalog.repositories;

import com.concordeu.catalog.entities.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    @Query(value = """
            SELECT * FROM comments WHERE PRODUCT_ID = ?1
            """, nativeQuery = true)
    Page<Comment> findAllByProductIdByPage(UUID productId, Pageable pageable);

    @Query(value = """
            SELECT * FROM comments WHERE AUTHOR = ?1
            """, nativeQuery = true)
    Page<Comment> findAllByAuthorByPage(String author, Pageable pageable);

    List<Comment> findAllByProductName(String productName);
}
