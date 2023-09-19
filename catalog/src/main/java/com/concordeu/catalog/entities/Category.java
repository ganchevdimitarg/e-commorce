package com.concordeu.catalog.entities;

import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.CreatedBy;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity(name = "Category")
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "category_name", columnNames = "name")
        },
        indexes = @Index(name = "category_index", columnList = "name"))
@NamedEntityGraph(
        name = "graph-catalog-category",
        attributeNodes = @NamedAttributeNode(value = "products", subgraph = "catalog-products"),
        subgraphs = @NamedSubgraph(name = "catalog-products", attributeNodes = @NamedAttributeNode("category")))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(unique = true, nullable = false, updatable = false)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "name", length = 200, unique = true, nullable = false)
    @Length(max = 200)
    private String name;

    @CreationTimestamp
    @Column(name = "create_on",updatable = false)
    private OffsetDateTime createOn;

    @UpdateTimestamp
    @Column(name = "update_on")
    private OffsetDateTime updateOn;

    @Builder.Default
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private Set<Product> products = new HashSet<>();

    /*public void setProducts(Product product) {
        this.products.add(product);
        product.setCategory(this);
    }*/

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(name, category.name) && Objects.equals(createOn, category.createOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, createOn);
    }
}

