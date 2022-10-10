package com.concordeu.catalog.category;

import com.concordeu.catalog.UniqueIdGenerator;
import com.concordeu.catalog.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity(name = "Category")
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "category_name", columnNames = "name")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends UniqueIdGenerator {
    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<Product> product;
}
