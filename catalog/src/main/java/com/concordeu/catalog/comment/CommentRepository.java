package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findAllByProduct(Product product);
    List<Comment> findAllByAuthor(String author);
}
