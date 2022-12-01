package com.concordeu.catalog.dao;

import com.concordeu.catalog.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface CommentDao extends JpaRepository<Comment, String> {
    @Query(value = """
            SELECT * FROM comments WHERE PRODUCT_ID = ?1
            """, nativeQuery = true)
    Page<Comment> findAllByProductIdByPage(String productId, Pageable pageable);

    @Query(value = """
            SELECT * FROM comments WHERE AUTHOR = ?1
            """, nativeQuery = true)
    Page<Comment> findAllByAuthorByPage(String author, Pageable pageable);

    List<Comment> findAllByProductName(String productName);
}
