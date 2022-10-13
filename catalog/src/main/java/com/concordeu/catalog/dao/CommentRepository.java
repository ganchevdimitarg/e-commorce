package com.concordeu.catalog.dao;

import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findAllByProduct(Product product);
    List<Comment> findAllByAuthor(String author);
}
