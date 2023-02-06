package com.concordeu.order.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

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
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "item_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(name = "quantity", nullable = false)
    private long quantity;
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
}