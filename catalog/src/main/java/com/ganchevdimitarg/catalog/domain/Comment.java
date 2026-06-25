package com.concordeu.catalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Entity(name = "Comment")
@Table(name = "comments",
        indexes = @Index(name = "comment_index",columnList = "author"))
@SQLDelete(sql = "UPDATE comments SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Comment extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private UUID id;
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
    @ManyToOne
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;
}
