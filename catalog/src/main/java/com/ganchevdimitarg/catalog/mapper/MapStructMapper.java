package com.concordeu.catalog.mapper;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.category.CategoryRequestDto;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.dto.comment.CreateCommentCommand;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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

    CreateProductCommand mapProductRequestToCreateCommand(ProductRequestDto dto, String categoryName);
    UpdateProductCommand mapProductRequestToUpdateCommand(ProductRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Product mapCreateCommandToProduct(CreateProductCommand cmd);

    CreateCommentCommand mapCommentRequestToCreateCommand(CommentRequestDto dto, String productName);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    Comment mapCreateCommentCommandToComment(CreateCommentCommand cmd);
}
