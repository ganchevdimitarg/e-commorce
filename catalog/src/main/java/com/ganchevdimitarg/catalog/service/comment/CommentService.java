package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.dto.comment.CreateCommentCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponseDto createComment(CreateCommentCommand command);
    Page<CommentResponseDto> findAllByProductNameByPage(String productName, Pageable pageable);
    Page<CommentResponseDto> findAllByAuthorByPage(String author, Pageable pageable);
    double getAvgStars(String productName);
}
