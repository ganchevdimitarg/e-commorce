package com.concordeu.catalog.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@NoArgsConstructor
@Setter
@Getter
public class ProductDTO {
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