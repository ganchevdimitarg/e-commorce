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
    public ResponseEntity<CommentDto> createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName) {
        CommentDto commentDto = mapper.mapCommentRequestDtoToCommentDto(requestDto);
        validator.validateData(commentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(commentDto, productName));
    }

    @GetMapping("/get-comments-product-name/{productName}")
    public ResponseEntity<Page<CommentDto>> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.findAllByProductNameByPage(productName, page, pageSize));
    }

    @GetMapping("/get-comments-author/{author}")
    public ResponseEntity<Page<CommentDto>> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.findAllByAuthorByPage(author, page, pageSize));
    }

    @GetMapping("/get-avg-stars/{productName}")
    public ResponseEntity<Double> getAvgStars(@PathVariable String productName) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.getAvgStars(productName));
    }

}
