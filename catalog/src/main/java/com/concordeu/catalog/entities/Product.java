package com.concordeu.catalog.entities;

import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity(name = "Product")
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "product_name", columnNames = "name")
        },
        indexes = @Index(name = "product_index", columnList = "name"))
@NamedEntityGraph(
        name = "graph-catalog-product",
        attributeNodes = @NamedAttributeNode(value = "comments", subgraph = "catalog-comments"),
        subgraphs = @NamedSubgraph(name = "catalog-comments", attributeNodes = @NamedAttributeNode("product")))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Product {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(unique = true, nullable = false, updatable = false)
    private UUID id;
    @Version
    private Long version;
    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Length(min = 3, max = 300)
    private String name;
    @Column(name = "description", nullable = false)
    @NotBlank
    @Length(min = 10, max = 150)
    private String description;
    @Column(name = "price", nullable = false)
    private BigDecimal price;
    @Column(name = "stock")
    private boolean inStock;
    @Column(name = "characteristics")
    private String characteristics;
    @CreationTimestamp
    @Column(name = "create_on",updatable = false)
    private OffsetDateTime createOn;
    @UpdateTimestamp
    @Column(name = "update_on")
    private OffsetDateTime updateOn;
    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;
    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, targetEntity = Comment.class, cascade = CascadeType.PERSIST)
    private Set<Comment> comments = new HashSet<>();

    /*public void setComments(Comment comment) {
        this.comments.add(comment);
        comment.setProduct(this);
    }*/

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return inStock == product.inStock && Objects.equals(name, product.name) && Objects.equals(description, product.description) && Objects.equals(price, product.price) && Objects.equals(characteristics, product.characteristics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, price, inStock, characteristics);
    }
}

