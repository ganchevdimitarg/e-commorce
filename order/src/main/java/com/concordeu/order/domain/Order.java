package com.concordeu.order.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


@Table("orders")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Order {
    @Id
    private Long id;
    @Column("order_number")
    private Long orderNumber;
    @Column("username")
    private String username;
    @Column("delivery_comment")
    private String deliveryComment;
    @CreatedDate
    @Column("created_on")
    private LocalDateTime createdOn;
    @LastModifiedDate
    @Column("updated_on")
    private LocalDateTime updatedOn;
    @Transient
    private Charge charge;
    @Transient
    @Builder.Default
    private List<Item> items = new ArrayList<>();
}
