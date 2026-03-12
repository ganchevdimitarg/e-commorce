package com.ganchevdimitarg.catalog.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity(name = "Comment")
@Table(name = "comments",
        indexes = @Index(name = "comment_index",columnList = "author"))
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @Column(name = "author", length = 200)
    private String author;
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;
}

