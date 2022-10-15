package com.concordeu.catalog.dto;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
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
    private List<Product> products;

    public static CategoryDto convertCategory(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .products(category.getProducts())
                .build();
    }
}
