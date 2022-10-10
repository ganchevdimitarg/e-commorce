package com.concordeu.catalog.comment;

import com.concordeu.catalog.UniqueIdGenerator;
import com.concordeu.catalog.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity(name = "Comment")
@Table(name = "comments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Comment extends UniqueIdGenerator {
    @Column(name = "title", columnDefinition = "TEXT")
    @Size(min = 3, max = 15)
    private String title;
    @Column(name = "text", columnDefinition = "TEXT")
    @Size(min = 10, max = 150)
    private String text;
    @Column(name = "star", nullable = false)
    private double star;
    @Column(name = "author", columnDefinition = "TEXT")
    private String author;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
}

