package com.concordeu.order.domain;

import lombok.*;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Entity(name = "Items")
@Table(
        name = "items",
        uniqueConstraints = {
                @UniqueConstraint(name = "product_id", columnNames = "product_id")
        },
        indexes = @Index(name = "product_id_index", columnList = "product_id"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Item {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @GeneratedValue
    @Column(name = "item_id",length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(name = "quantity", nullable = false)
    private long quantity;
    @CreationTimestamp
    @Column(name = "create_on",updatable = false)
    private OffsetDateTime createOn;
    @UpdateTimestamp
    @Column(name = "update_on")
    private OffsetDateTime updateOn;
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
}