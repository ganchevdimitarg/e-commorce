package com.concordeu.catalog.controller;

import com.concordeu.client.catalog.comment.CommentResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.client.catalog.comment.CommentRequestDto;
import com.concordeu.catalog.validator.CommentDataValidator;
import com.concordeu.catalog.service.comment.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comment")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final CommentDataValidator validator;
    private final MapStructMapper mapper;

    @PostMapping("/create-comment/{productName}")
    public CommentResponseDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        CommentResponseDto commentResponseDto = mapper.mapCommentRequestDtoToCommentResponseDto(requestDto);
        validator.validateData(commentResponseDto);
        return commentService.createComment(commentResponseDto, productName);
    }

    @GetMapping("/get-comments-product-name/{productName}")
    public Page<CommentResponseDto> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName) {
        return commentService.findAllByProductNameByPage(productName, page, pageSize);
    }

    @GetMapping("/get-comments-author/{author}")
    public Page<CommentResponseDto> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author) {
        return commentService.findAllByAuthorByPage(author, page, pageSize);
    }

    @GetMapping("/get-avg-stars/{productName}")
    public double getAvgStars(@PathVariable String productName) {
        return commentService.getAvgStars(productName);
    }

}
