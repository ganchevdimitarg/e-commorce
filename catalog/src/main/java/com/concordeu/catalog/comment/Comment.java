package com.concordeu.catalog.comment;

import com.concordeu.catalog.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity(name = "Comment")
@Table(name = "comments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Comment {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
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
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;
}

