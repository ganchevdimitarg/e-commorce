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

    @Query("select avg(c.star) from Comment c where c.product.id = :productId")
    Double findAverageStarByProductId(@Param("productId") UUID productId);
}
