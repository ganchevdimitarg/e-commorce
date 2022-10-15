package com.concordeu.catalog.dto;

import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Comment;
import com.concordeu.catalog.domain.Product;
import lombok.*;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
@ToString
public class ProductDto {
    @NotEmpty
    @Size(min = 3, max = 20)
    private String name;
    @NotEmpty
    @Size(min = 10, max = 50)
    private String description;
    private BigDecimal price;
    private boolean inStock;
    private String characteristics;
    private Category category;
    private List<Comment> comments;

    public static ProductDto convertProduct(Product product){
        return ProductDto.builder()
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .inStock(product.isInStock())
                .characteristics(product.getCharacteristics())
                .category(product.getCategory())
                .comments(product.getComments())
                .build();
    }
}