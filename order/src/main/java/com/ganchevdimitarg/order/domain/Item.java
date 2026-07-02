package com.ganchevdimitarg.order.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity(name = "Items")
@Table(
        name = "items",
        uniqueConstraints = {
                @UniqueConstraint(name = "product_id", columnNames = "product_id")
        },
        indexes = @Index(name = "product_id_index", columnList = "product_id"))
@SQLDelete(sql = "UPDATE items SET deleted_at = now() WHERE item_id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Item extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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