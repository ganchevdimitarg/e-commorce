package com.concordeu.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Entity(name = "Product")
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "product_name", columnNames = "name")
        },
        indexes = @Index(name = "product_index",columnList = "name"))
@SQLDelete(sql = "UPDATE products SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@Getter
public class Product extends Auditable {
    @Id
    @Setter
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Setter
    @Column(name = "name", nullable = false, length = 20)
    @NotEmpty
    @Size(min = 3, max = 20)
    private String name;
    @Setter
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    @NotEmpty
    @Size(min = 10, max = 50)
    private String description;
    @Setter
    @Column(name = "price", nullable = false)
    private BigDecimal price;
    @Setter
    @Column(name = "stock")
    private boolean inStock;
    @Setter
    @Column(name = "characteristics", columnDefinition = "TEXT")
    private String characteristics;
    @Setter
    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;
    @Setter
    @OneToMany(mappedBy = "product", targetEntity = Comment.class, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JsonIgnore
    private List<Comment> comments;

    public List<Comment> getComments() {
        return comments == null ? List.of() : List.copyOf(comments);
    }
}
