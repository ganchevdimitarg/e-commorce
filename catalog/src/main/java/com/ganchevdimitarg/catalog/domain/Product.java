package com.ganchevdimitarg.catalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "Product")
@Table(name = "products")
@SQLDelete(sql = "UPDATE products SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@Getter
@Setter
public class Product extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private UUID id;
    @Column(name = "name", nullable = false, length = 200)
    @NotBlank
    @Size(min = 3, max = 200)
    private String name;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    @Size(min = 10, max = 2000)
    private String description;
    @Column(name = "price", nullable = false)
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    private BigDecimal price;
    @Column(name = "stock")
    private boolean inStock;
    // Derived from the product's live comments and refreshed in the comment mutation's
    // transaction; product writes never set them directly.
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;
    @Column(name = "avg_stars", nullable = false)
    private double avgStars;
    @Column(name = "characteristics", columnDefinition = "TEXT")
    private String characteristics;
    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;
}
