package com.concordeu.catalog.comment;

import com.concordeu.catalog.BasicProduct;
import com.concordeu.catalog.product.Product;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
public class Comment extends BasicProduct {
    @Column(name = "title")
    @Size(min = 3, max = 15)
    private String title;
    @Column(name = "text")
    @Size(min = 10, max = 150)
    private String text;
    @Column(name = "star", nullable = false)
    private double star;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
}
