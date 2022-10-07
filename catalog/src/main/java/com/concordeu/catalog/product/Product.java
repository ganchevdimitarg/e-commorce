package com.concordeu.catalog.product;

import com.concordeu.catalog.BasicProduct;
import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.comment.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product extends BasicProduct {
    @Column(name = "name", nullable = false, unique = true)
    @NotEmpty
    @Size(min = 3, max = 20)
    private String name;
    @Column(name = "description", nullable = false)
    @NotEmpty
    @Size(min = 10, max = 50)
    private String description;
    @Column(name = "price", nullable = false)
    private BigDecimal price;
    @Column(name = "stock")
    private boolean inStock;
    @Column(name = "characteristics")
    private String characteristics;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<Comment> comment;
}

