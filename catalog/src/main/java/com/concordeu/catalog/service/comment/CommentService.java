package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.CommentDto;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentDto createComment(CommentDto commentDto, String productName);
    Page<CommentDto> findAllByProductNameByPage(String productName, int page, int pageSize);
    Page<CommentDto> findAllByAuthorByPage(String author, int page, int pageSize);
    double getAvgStars(String productName);
}

