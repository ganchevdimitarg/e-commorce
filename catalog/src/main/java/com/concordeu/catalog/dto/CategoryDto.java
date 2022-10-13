package com.concordeu.catalog.dto;

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
