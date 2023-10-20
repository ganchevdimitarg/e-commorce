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

@Table("charges")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Charge {
    @Id
    private Long id;
    @Column("charge_id_stp")
    private String chargeId;
    @Column("status")
    private String status;
    @Column("amount")
    private Long amount;
    @Column("currency")
    private String currency;
    @CreatedDate
    @Column("created_on")
    private LocalDateTime createdOn;
    @LastModifiedDate
    @Column("updated_on")
    private LocalDateTime updatedOn;
    @Column("order_id")
    private Long orderId;
}
