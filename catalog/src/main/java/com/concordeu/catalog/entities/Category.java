package com.concordeu.catalog.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;
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
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "name", length = 200, unique = true, nullable = false)
    @Length(max = 200)
    private String name;

    @CreationTimestamp
    private LocalDateTime createOn;

    @UpdateTimestamp
    private LocalDateTime updateOn;

    @Builder.Default
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.PERSIST)
    private Set<Product> products = new HashSet<>();

    public void setProducts(Product product) {
        this.products.add(product);
        product.setCategory(this);
    }

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

