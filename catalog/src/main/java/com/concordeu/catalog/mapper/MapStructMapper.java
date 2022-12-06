package com.concordeu.catalog.mapper;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryRequestDto;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    Product mapProductResponseDtoToProduct(ProductResponseDto productResponseDto);
    ProductRequestDto mapProductToProductRequestDto(Product product);
    ProductResponseDto mapProductToProductResponseDto(Product product);
    List<Product> mapProductResponseDtosToProducts(List<ProductResponseDto> productResponseDtos);
    List<ProductRequestDto> mapProductsToProductRequestDtos(List<Product> product);
    ProductResponseDto mapProductRequestDtoToProductResponseDto(ProductRequestDto requestDto);

    Category mapCategoryResponseDtoToCategory(CategoryResponseDto categoryResponseDTO);
    CategoryResponseDto mapCategoryToCategoryResponseDto(Category category);
    List<Category> mapCategoryResponseDtosToCategories(List<CategoryResponseDto> categoryResponseDto);
    List<CategoryResponseDto> mapCategoriesToCategoryResponseDtos(List<Category> category);
    CategoryResponseDto mapCategoryRequestDtoToCategoryDto(CategoryRequestDto requestDto);

    Comment mapCommentResponseDtoToComment(CommentResponseDto commentResponseDto);
    CommentResponseDto mapCommentToCommentResponseDto(Comment comment);
    List<Comment> mapCommentResponseDtosToComments(List<CommentResponseDto> commentResponseDtos);
    List<CommentResponseDto> mapCommentsToCommentResponseDtos(List<Comment> comments);
    CommentResponseDto mapCommentRequestDtoToCommentResponseDto(CommentRequestDto commentRequestDto);
}
