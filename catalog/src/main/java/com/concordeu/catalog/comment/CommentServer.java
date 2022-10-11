package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductDto;

import java.util.List;
import java.util.Optional;

public interface CommentServer {
    CommentDto createComment(CommentDto commentDto, String productName);
    List<CommentDto> findAllByProductName(String productName);
    List<CommentDto> findAllByAuthor(String author);
}
