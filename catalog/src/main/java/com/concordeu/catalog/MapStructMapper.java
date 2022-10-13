package com.concordeu.catalog;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.dto.*;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    Product mapDtoToProduct(ProductDto productDto);
    ProductDto mapProductToDto(Product product);
    List<Product> mapProductDtosToProducts(List<ProductDto> productDtos);
    List<ProductDto> mapProductsToDtos(List<Product> product);
    ProductDto mapProductRequestDtoToProductDto(ProductRequestDto requestDto);

    Category mapDtoToCategory(CategoryDto categoryDTO);
    CategoryDto mapCategoryToDto(Category category);
    List<Category> mapDtosToCategories(List<CategoryDto> categoryDto);
    List<CategoryDto> mapCategoriesToDtos(List<Category> category);
    CategoryDto mapCategoryRequestDtoToCategoryDto(CategoryRequestDto requestDto);

    Comment mapDtoToComment(CommentDto categoryDTO);
    CommentDto mapCommentToDto(Comment category);
    List<Comment> mapDtosToComments(List<CommentDto> categoryDto);
    List<CommentDto> mapCommentsToDtos(List<Comment> category);
    CommentDto mapCommentRequestDtoToCommentDto(CommentRequestDto requestDto);
}
