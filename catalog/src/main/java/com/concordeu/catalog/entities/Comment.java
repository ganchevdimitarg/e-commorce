package com.concordeu.catalog.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "Comment")
@Table(name = "comments",
        indexes = @Index(name = "comment_index",columnList = "author"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Comment {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "title", columnDefinition = "TEXT")
    @Length(min = 3, max = 15)
    @NotBlank
    private String title;

    @Column(name = "text", columnDefinition = "TEXT")
    @Length(min = 10, max = 150)
    @NotBlank
    private String text;

    @Column(name = "star", nullable = false)
    private double star;

    @Column(name = "author", length = 200)
    private String author;

    @CreationTimestamp
    private LocalDateTime createOn;

    @UpdateTimestamp
    private LocalDateTime updateOn;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment comment = (Comment) o;
        return Double.compare(comment.star, star) == 0 && Objects.equals(title, comment.title) && Objects.equals(text, comment.text) && Objects.equals(author, comment.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, text, star, author);
    }
}

