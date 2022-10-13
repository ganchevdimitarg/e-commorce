package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.CommentDto;

import java.util.List;

public interface CommentService {
    CommentDto createComment(CommentDto commentDto, String productName);
    List<CommentDto> findAllByProductName(String productName);
    List<CommentDto> findAllByAuthor(String author);
    double getAvgStars(String productName);
}

