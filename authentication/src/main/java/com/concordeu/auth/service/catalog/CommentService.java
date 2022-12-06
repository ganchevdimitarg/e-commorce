package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.comment.CommentRequestDto;
import com.concordeu.client.catalog.comment.CommentResponseDto;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentResponseDto createComment(CommentRequestDto requestDto, String productName);

    Page<CommentResponseDto> findAllByProductNameByPage(int page, int pageSize, String productName);

    Page<CommentResponseDto> findAllByAuthorByPage(int page, int pageSize, String author);

    double getAvgStars(String productName);
}
