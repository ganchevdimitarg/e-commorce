package com.concordeu.catalog.product;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDto mapProductToDto(Product source);
    Product mapDTOToProduct(ProductDto destination);

    List<Product> mapProductDtosToProducts(List<ProductDto> productDtos);

    List<ProductDto> mapProductsToDtos(List<Product> productDTOs);
}
