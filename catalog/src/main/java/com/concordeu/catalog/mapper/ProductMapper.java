package com.concordeu.catalog.mapper;

import com.concordeu.catalog.dto.ProductDTO;
import com.concordeu.catalog.entities.Product;
import org.mapstruct.Mapper;

import java.util.Set;

@Mapper
public interface ProductMapper {
    Product mapProductDTOToProduct(ProductDTO productDTO);
    ProductDTO mapProductToProductDTO(Product product);
    Set<Product> mapProductDTOToProduct(Set<ProductDTO> productDTO);
    Set<ProductDTO> mapProductToProductDTO(Set<Product> product);
}
