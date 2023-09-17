package com.concordeu.catalog.dto;

import com.concordeu.catalog.entities.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ProductDTO implements Serializable {
    private UUID id;
    @NotBlank
    @NotNull
    @Size(min = 3, max = 20)
    private String name;
    @NotBlank
    @NotNull
    @Size(min = 10, max = 50)
    private String description;
    @NotNull
    private BigDecimal price;
    private boolean inStock;
    private String characteristics;
    private OffsetDateTime createOn;
    private OffsetDateTime updateOn;
    private String categoryName;
//    private Set<Comment> comments;

    public static ProductDTO mapProductToProductDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .characteristics(product.getCharacteristics())
                .price(product.getPrice())
                .inStock(product.isInStock())
                .createOn(product.getCreateOn())
                .updateOn(product.getUpdateOn())
                .categoryName(product.getCategory().getName())
//                .comments(product.getComments())
                .build();
    }
}
