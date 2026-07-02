package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.dto.PageResponse;
import com.ganchevdimitarg.catalog.dto.comment.CommentRequestDto;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Create comment", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/products/{productName}/comments")
    public ResponseEntity<CommentResponseDto> createComment(@PathVariable @NotBlank String productName,
                                                            @RequestBody @Valid CommentRequestDto requestDto) {
        CommentResponseDto created = commentService.createComment(new CreateCommentCommand(
                requestDto.title(), requestDto.text(), requestDto.star(), requestDto.author(), productName));
        return ResponseEntity.created(
                URI.create("/api/v1/catalog/products/" + productName + "/comments")).body(created);
    }

    @Operation(summary = "List comments by product", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/products/{productName}/comments")
    public PageResponse<CommentResponseDto> findAllByProductName(@PathVariable @NotBlank String productName,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(commentService.findAllByProductNameByPage(productName, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "List comments by author", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/comments/by-author/{author}")
    public PageResponse<CommentResponseDto> findAllByAuthor(@PathVariable @NotBlank String author,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(commentService.findAllByAuthorByPage(author, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Average stars for product", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/products/{productName}/comments/avg-stars")
    public double getAvgStars(@PathVariable @NotBlank String productName) {
        return commentService.getAvgStars(productName);
    }
}
