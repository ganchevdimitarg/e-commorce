package com.ganchevdimitarg.catalog.mapper;

import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Comment;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.category.CategorySummaryDto;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    Product mapProductResponseDtoToProduct(ProductResponseDto productResponseDto);
    ProductRequestDto mapProductToProductRequestDto(Product product);
    ProductResponseDto mapProductToProductResponseDto(Product product);
    List<Product> mapProductResponseDtosToProducts(List<ProductResponseDto> productResponseDtos);
    ProductResponseDto mapProductRequestDtoToProductResponseDto(ProductRequestDto requestDto);

    CategorySummaryDto mapCategoryToCategorySummaryDto(Category category);

    Comment mapCommentResponseDtoToComment(CommentResponseDto commentResponseDto);
    CommentResponseDto mapCommentToCommentResponseDto(Comment comment);
    List<Comment> mapCommentResponseDtosToComments(List<CommentResponseDto> commentResponseDtos);
    List<CommentResponseDto> mapCommentsToCommentResponseDtos(List<Comment> comments);

    CreateProductCommand mapProductRequestToCreateCommand(ProductRequestDto dto, String categoryName);
    UpdateProductCommand mapProductRequestToUpdateCommand(UpdateProductRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Product mapCreateCommandToProduct(CreateProductCommand cmd);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    Comment mapCreateCommentCommandToComment(CreateCommentCommand cmd);
}
