package com.concordeu.order.entities;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "Orders")
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "order_number", columnNames = "order_number")
        },
        indexes = @Index(name = "order_number_index",columnList = "order_number"))
@NamedEntityGraph(
        name = "graph-order",
        attributeNodes = {
                @NamedAttributeNode(value = "items", subgraph = "order-items"),
                @NamedAttributeNode(value = "charge", subgraph = "order-charge")
        },
        subgraphs = {
                @NamedSubgraph(name = "order-items", attributeNodes = @NamedAttributeNode("order")),
                @NamedSubgraph(name = "order-charge", attributeNodes = @NamedAttributeNode("order"))
        })
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
    @Column(name = "order_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "order_number", unique = true, nullable = false)
    private long orderNumber;
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