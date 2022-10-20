package com.concordeu.auth.controller;

import com.concordeu.client.catalog.comment.CommentRequestDto;
import com.concordeu.client.catalog.comment.CommentResponseDto;
import com.concordeu.auth.service.catalog.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comment")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/create-comment/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    public CommentResponseDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        return commentService.createComment(requestDto, productName);
    }

    @GetMapping("/get-comments-product-name/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Page<CommentResponseDto> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName) {
        return commentService.findAllByProductNameByPage(page, pageSize, productName);
    }

    @GetMapping("/get-comments-author/{author}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Page<CommentResponseDto> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author) {
        return commentService.findAllByAuthorByPage(page, pageSize, author);
    }

    @GetMapping("/get-avg-stars/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public double getAvgStars(@PathVariable String productName) {
        return commentService.getAvgStars(productName);
    }


}
