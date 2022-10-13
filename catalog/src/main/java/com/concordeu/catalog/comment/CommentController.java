package com.concordeu.catalog.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comment")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final CommentDataValidator validator;

    @PostMapping("/create-comment/{productName}")
    public ResponseEntity<CommentDto> createComment(@RequestBody CommentDto request, @PathVariable String productName) {
        validator.validateData(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(request, productName));
    }

    @GetMapping("/get-comments-product-name/{productName}")
    public ResponseEntity<List<CommentDto>> findAllByProductName(@PathVariable String productName) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.findAllByProductName(productName));
    }

    @GetMapping("/get-comments-author/{author}")
    public ResponseEntity<List<CommentDto>> findAllByAuthor(@PathVariable String author) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.findAllByAuthor(author));
    }

    @GetMapping("/get-avg-stars/{productName}")
    public ResponseEntity<Double> getAvgStars(@PathVariable String productName) {
        return ResponseEntity.status(HttpStatus.OK).body(commentService.getAvgStars(productName));
    }

}
