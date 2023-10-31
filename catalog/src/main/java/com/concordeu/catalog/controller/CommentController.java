package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.CommentDTO;
import com.concordeu.catalog.mapper.CommentMapper;
import com.concordeu.catalog.service.comment.CommentService;
import com.concordeu.catalog.validator.CommentDataValidator;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/comment")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final CommentDataValidator validator;

    @Operation(summary = "Create Comment",  description = "Create a comment for the product and save it in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-comment")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @Observed(
            name = "user.name",
            contextualName = "createComment",
            lowCardinalityKeyValues = {"method", "createComment"}
    )
    public CommentDTO createComment(@RequestBody CommentDTO requestDto,
                                            @RequestParam String productName) {
        validator.validateData(requestDto);
        return commentService.createComment(requestDto, productName);
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
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Observed(
            name = "user.name",
            contextualName = "findAllByProductName",
            lowCardinalityKeyValues = {"method", "findAllByProductName"}
    )
        public Page<CommentDTO> findAllByProductName(@RequestParam int page,
                                                             @RequestParam int size,
                                                             @RequestParam String productName) {
        return commentService.findAllByProductNameByPage(productName, page, size);
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
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Observed(
            name = "user.name",
            contextualName = "findAllByAuthor",
            lowCardinalityKeyValues = {"method", "findAllByAuthor"}
    )
    public Page<CommentDTO> findAllByAuthor(@RequestParam int page,
                                                    @RequestParam int size,
                                                    @RequestParam String author) {
        return commentService.findAllByAuthorByPage(author, page, size);
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
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Observed(
            name = "user.name",
            contextualName = "getAvgStars",
            lowCardinalityKeyValues = {"method", "getAvgStars"}
    )
    public double getAvgStars(@RequestParam String productName) {
        return commentService.getAvgStars(productName);
    }

}
