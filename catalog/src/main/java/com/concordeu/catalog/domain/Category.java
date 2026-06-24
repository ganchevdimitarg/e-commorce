package com.concordeu.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import java.util.List;

@Entity(name = "Category")
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "category_name", columnNames = "name")
        },
        indexes = @Index(name = "category_index",columnList = "name"))
@SQLDelete(sql = "UPDATE categories SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@Getter
public class Category extends Auditable {
    @Id
    @Setter
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Setter
    @Column(name = "name", unique = true, nullable = false, length = 200)
    private String name;
    @Setter
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Product> products;

    public List<Product> getProducts() {
        return products == null ? List.of() : List.copyOf(products);
    }
}
