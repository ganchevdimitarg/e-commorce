package com.concordeu.catalog.service.comment;

import com.concordeu.catalog.dto.CommentDTO;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentDTO createComment(CommentDTO commentDTO, String productName);
    Page<CommentDTO> findAllByProductNameByPage(String productName, int page, int pageSize);
    Page<CommentDTO> findAllByAuthorByPage(String author, int page, int pageSize);
    double getAvgStars(String productName);
}

