package com.concordeu.order.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Entity(name = "Charges")
@Table(name = "charges")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Charge {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "charge_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "charge_id_payment", nullable = false)
    private String chargeId;
    @Column(name = "status", nullable = false)
    private String status;
    @OneToOne()
    @JoinColumn(name = "order_id")
    private Order order;
}
