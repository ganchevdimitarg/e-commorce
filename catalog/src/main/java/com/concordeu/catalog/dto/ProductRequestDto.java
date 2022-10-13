package com.concordeu.catalog.dto;

import lombok.*;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ProductRequestDto {
    @NotEmpty
    @Size(min = 3, max = 20)
    private String name;
    @NotEmpty
    @Size(min = 10, max = 50)
    private String description;
    private BigDecimal price;
    private boolean inStock;
    private String characteristics;
}
