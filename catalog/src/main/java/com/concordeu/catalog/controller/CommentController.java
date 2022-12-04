package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.validator.CommentDataValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/comment")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final CommentDataValidator validator;
    private final MapStructMapper mapper;

    @Operation(summary = "Create Comment",  description = "Create a comment for the product and save it in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-comment")
    public CommentResponseDto createComment(@RequestBody CommentRequestDto requestDto,
                                            @RequestParam String productName) {
        CommentResponseDto commentResponseDto = mapper.mapCommentRequestDtoToCommentResponseDto(requestDto);
        validator.validateData(commentResponseDto);
        return commentService.createComment(commentResponseDto, productName);
    }

    @Operation(summary = "Get Comments Product Name",  description = "Get Comments By Product Name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-comments-product-name")
        public Page<CommentResponseDto> findAllByProductName(@RequestParam int page,
                                                             @RequestParam int size,
                                                             @RequestParam String productName) {
        return commentService.findAllByProductNameByPage(productName, page, size);
    }

    @Operation(summary = "Get Comment Author",  description = "Get an author's comment on the product",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-comments-author")
    public Page<CommentResponseDto> findAllByAuthor(@RequestParam int page,
                                                    @RequestParam int size,
                                                    @RequestParam String author) {
        return commentService.findAllByAuthorByPage(author, page, size);
    }

    @Operation(summary = "Get Average Stars",  description = "Get Get average stars for the product",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-avg-stars")
    public double getAvgStars(@RequestParam String productName) {
        return commentService.getAvgStars(productName);
    }

}
