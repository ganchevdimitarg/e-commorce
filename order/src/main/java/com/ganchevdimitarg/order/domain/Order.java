package com.ganchevdimitarg.order.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "Orders")
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "order_number", columnNames = "order_number")
        },
        indexes = @Index(name = "order_number_index",columnList = "order_number"))
@SQLDelete(sql = "UPDATE orders SET deleted_at = now() WHERE order_id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Order extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "order_number", unique = true, nullable = false)
    private long orderNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    @Column(name = "username", nullable = false)
    private String username;
    @Column(name = "created_on")
    private LocalDateTime createdOn;
    @Column(name = "delivery_comment")
    private String deliveryComment;
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Charge charge;
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Item> items;
}
