package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.comment.CommentResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponseDto createComment(CommentResponseDto commentResponseDto, String productName);
    Page<CommentResponseDto> findAllByProductNameByPage(String productName, Pageable pageable);
    Page<CommentResponseDto> findAllByAuthorByPage(String author, Pageable pageable);
    double getAvgStars(String productName);
}

