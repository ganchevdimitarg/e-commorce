package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.comment.CommentResponseDto;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentResponseDto createComment(CommentResponseDto commentResponseDto, String productName);
    Page<CommentResponseDto> findAllByProductNameByPage(String productName, int page, int pageSize);
    Page<CommentResponseDto> findAllByAuthorByPage(String author, int page, int pageSize);
    double getAvgStars(String productName);
}

