package com.concordeu.catalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

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
    @Column(name = "author", length = 200)
    private String author;
    @ManyToOne
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;
}
