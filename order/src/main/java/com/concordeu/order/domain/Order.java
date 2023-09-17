package com.concordeu.order.domain;

import lombok.*;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
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
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @GeneratedValue
    @Column(name = "order_id",length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "order_number", unique = true, nullable = false)
    private long orderNumber;
    @Column(name = "username", nullable = false)
    private String username;
    @Column(name = "created_on")
    private OffsetDateTime createdOn;
    @Column(name = "delivery_comment")
    private String deliveryComment;
    @CreationTimestamp
    @Column(name = "create_on",updatable = false)
    private OffsetDateTime createOn;
    @UpdateTimestamp
    @Column(name = "update_on")
    private OffsetDateTime updateOn;
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Charge charge;
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Item> items;
}
