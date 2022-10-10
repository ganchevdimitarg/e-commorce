package com.concordeu.catalog.product;

import com.concordeu.catalog.UniqueIdGenerator;
import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.comment.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends UniqueIdGenerator {
    @Column(name = "name", updatable = false, nullable = false, columnDefinition = "TEXT")
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
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<Comment> comment;
}

