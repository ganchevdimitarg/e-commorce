package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.PageResponse;
import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/comment")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CommentController {

    private final CommentService commentService;
    private final MapStructMapper mapper;

    @Operation(summary = "Create Comment",  description = "Create a comment for the product and save it in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-comment")
    public CommentResponseDto createComment(@RequestBody @Valid CommentRequestDto requestDto,
                                            @RequestParam String productName) {
        CommentResponseDto commentResponseDto = mapper.mapCommentRequestDtoToCommentResponseDto(requestDto);
        return commentService.createComment(commentResponseDto, productName);
    }

    @Operation(summary = "Get Comments Product Name",  description = "Get Comments By Product Name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-comments-product-name")
    public PageResponse<CommentResponseDto> findAllByProductName(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam String productName) {
        return PageResponse.of(commentService.findAllByProductNameByPage(productName, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Get Comment Author",  description = "Get an author's comment on the product",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-comments-author")
    public PageResponse<CommentResponseDto> findAllByAuthor(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam String author) {
        return PageResponse.of(commentService.findAllByAuthorByPage(author, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Get Average Stars",  description = "Get Get average stars for the product",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-avg-stars")
    public double getAvgStars(@RequestParam String productName) {
        return commentService.getAvgStars(productName);
    }

}
