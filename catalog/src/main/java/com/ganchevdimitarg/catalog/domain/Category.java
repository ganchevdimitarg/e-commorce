package com.ganchevdimitarg.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity(name = "Category")
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "category_name", columnNames = "name")
        },
        indexes = @Index(name = "category_index",columnList = "name"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "name", unique = true, nullable = false, length = 200)
    private String name;
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Product> products;
}

