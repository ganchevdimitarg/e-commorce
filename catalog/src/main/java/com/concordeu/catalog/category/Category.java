package com.concordeu.catalog.category;

import com.concordeu.catalog.BasicProduct;
import com.concordeu.catalog.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends BasicProduct {
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    @OneToOne(mappedBy = "category")
    private Product product;
}
