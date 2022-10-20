package com.concordeu.client.catalog.comment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "comment", url = "http://localhost:8081/api/v1/comment")
public interface CommentClient {

    @PostMapping("/create-comment/{productName}")
    CommentResponseDto createComment(@RequestBody CommentRequestDto requestDto, @PathVariable String productName);

    @GetMapping("/get-comments-product-name/{productName}")
    Page<CommentResponseDto> findAllByProductName(@RequestParam int page, int pageSize, @PathVariable String productName);

    @GetMapping("/get-comments-author/{author}")
    Page<CommentResponseDto> findAllByAuthor(@RequestParam int page, int pageSize, @PathVariable String author);

    @GetMapping("/get-avg-stars/{productName}")
    double getAvgStars(@PathVariable String productName);
}
