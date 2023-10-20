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

@Table("items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Item {
    @Id
    private Long id;
    @Column("product_id")
    private String productId;
    @Column("quantity")
    private long quantity;
    @CreatedDate
    @Column("created_on")
    private LocalDateTime createdOn;
    @LastModifiedDate
    @Column("updated_on")
    private LocalDateTime updatedOn;
    @Column("order_id")
    private Long orderId;
}