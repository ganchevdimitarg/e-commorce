package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;

import java.util.List;
import java.util.Optional;

public interface CommentServer {
    Optional<CommentDto> createComment(CommentDto commentDto, String productName);

    List<Comment> findAllByProduct(Product product);

    List<Comment> findAllByAuthor(String author);
}
