package com.concordeu.catalog.comment;

import java.util.List;

public interface CommentService {
    CommentDto createComment(CommentDto commentDto, String productName);
    List<CommentDto> findAllByProductName(String productName);
    List<CommentDto> findAllByAuthor(String author);
}
