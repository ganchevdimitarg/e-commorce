package com.concordeu.catalog.category;

import com.concordeu.catalog.UniqueIdGenerator;
import com.concordeu.catalog.product.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "Category")
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "category_name", columnNames = "name")
        })
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Category extends UniqueIdGenerator {
    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Product> products;
}

