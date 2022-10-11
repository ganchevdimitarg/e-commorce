package com.concordeu.catalog.category;

import com.concordeu.catalog.product.Product;
import com.concordeu.catalog.product.ProductDto;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class CategoryDto {
    private String id;
    private String name;
    private List<ProductDto> products;
}
