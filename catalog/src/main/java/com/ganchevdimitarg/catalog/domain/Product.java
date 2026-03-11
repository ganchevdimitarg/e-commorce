package com.ganchevdimitarg.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity(name = "Product")
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "product_name", columnNames = "name")
        },
        indexes = @Index(name = "product_index",columnList = "name"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    private com.ganchevdimitarg.catalog.domain.Category category;
    @OneToMany(mappedBy = "product", targetEntity = Comment.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Comment> comments;
}

