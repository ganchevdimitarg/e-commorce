package com.concordeu.auth.service.catalog;

import com.concordeu.client.catalog.comment.CommentClient;
import com.concordeu.client.catalog.comment.CommentRequestDto;
import com.concordeu.client.catalog.comment.CommentResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService{
    private final CommentClient commentClient;

    @Override
    public CommentResponseDto createComment(CommentRequestDto requestDto, String productName) {
        Assert.notNull(requestDto, "Request is empty");
        return commentClient.createComment(requestDto, productName);
    }

    @Override
    public Page<CommentResponseDto> findAllByProductNameByPage(int page, int pageSize, String productName) {
        Assert.notNull(productName, "Product name is empty");
        return commentClient.findAllByProductName(page, pageSize, productName);
    }

    @Override
    public Page<CommentResponseDto> findAllByAuthorByPage(int page, int pageSize, String author) {
        Assert.notNull(author, "Author name is empty");
        return commentClient.findAllByAuthor(page, pageSize, author);
    }

    @Override
    public double getAvgStars(String productName) {
        Assert.notNull(productName, "Product name is empty");
        return commentClient.getAvgStars(productName);
    }
}
