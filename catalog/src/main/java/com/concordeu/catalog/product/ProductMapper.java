package com.concordeu.catalog.product;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO mapProductToDto(Product source);
    Product mapDTOToProduct(ProductDTO destination);

    List<Product> mapProductDTOsToProducts(List<ProductDTO> productDTOs);

    List<ProductDTO> mapProductsToDtos(List<Product> productDTOs);
}
