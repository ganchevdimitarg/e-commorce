package com.concordeu.order.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Orders")
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "order_number", columnNames = "order_number")
        },
        indexes = @Index(name = "order_number_index",columnList = "order_number"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Order {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "order_number", unique = true, nullable = false)
    long orderNumber;
    @Column(name = "username", nullable = false)
    String username;
    @Column(name = "product_name", nullable = false)
    String productName;
    @Column(name = "quantity", nullable = false)
    long quantity;
    @Column(name = "created_on")
    LocalDateTime createdOn;
    @Column(name = "delivery_comment")
    String deliveryComment;
}
