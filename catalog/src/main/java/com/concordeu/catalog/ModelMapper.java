package com.concordeu.catalog;

import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryDto;
import com.concordeu.catalog.comment.Comment;
import com.concordeu.catalog.comment.CommentDto;
import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ModelMapper {
    Product mapDtoToProduct(ProductDto productDto);
    ProductDto mapProductToDto(Product product);
    List<Product> mapProductDtosToProducts(List<ProductDto> productDtos);
    List<ProductDto> mapProductsToDtos(List<Product> product);
    Category mapDtoToCategory(CategoryDto categoryDTO);
    CategoryDto mapCategoryToDto(Category category);
    List<Category> mapDtosToCategories(List<CategoryDto> categoryDto);
    List<CategoryDto> mapCategoriesToDtos(List<Category> category);

    Comment mapDtoToComment(CommentDto categoryDTO);
    CommentDto mapCommentToDto(Comment category);
    List<Comment> mapDtosToComments(List<CommentDto> categoryDto);
    List<CommentDto> mapCommentsToDtos(List<Comment> category);
}
