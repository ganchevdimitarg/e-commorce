package com.concordeu.catalog.controller;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.CommentRequestDto;
import com.concordeu.catalog.validator.CommentDataValidator;
import com.concordeu.catalog.dto.CommentDto;
import com.concordeu.catalog.service.comment.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public CommentDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        CommentDto commentDto = mapper.mapCommentRequestDtoToCommentDto(requestDto);
        validator.validateData(commentDto);
        return commentService.createComment(commentDto, productName);
    }

    @GetMapping("/get-comments-product-name/{productName}")
    public Page<CommentDto> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName) {
        return commentService.findAllByProductNameByPage(productName, page, pageSize);
    }

    @GetMapping("/get-comments-author/{author}")
    public Page<CommentDto> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author) {
        return commentService.findAllByAuthorByPage(author, page, pageSize);
    }

    @GetMapping("/get-avg-stars/{productName}")
    public double getAvgStars(@PathVariable String productName) {
        return commentService.getAvgStars(productName);
    }

}
