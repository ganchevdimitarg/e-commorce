package com.concordeu.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

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
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "name", nullable = false, length = 200)
    @NotEmpty
    @Size(min = 3, max = 20)
    private String name;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    @NotEmpty
    @Size(min = 10, max = 50)
    private String description;
    @Column(name = "price", nullable = false)
    private BigDecimal price;
    @Column(name = "stock")
    private boolean inStock;
    @Column(name = "characteristics", columnDefinition = "TEXT")
    private String characteristics;
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;
    @OneToMany(mappedBy = "product", targetEntity = Comment.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Comment> comments;
}

