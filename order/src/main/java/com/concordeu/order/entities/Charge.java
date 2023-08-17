package com.concordeu.order.entities;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;

@Entity(name = "Charges")
@Table(
        name = "charges",
        uniqueConstraints = {
                @UniqueConstraint(name = "charge_id_stp", columnNames = "charge_id_stp")
        },
        indexes = @Index(name = "charge_id_stp_index", columnList = "charge_id_stp"))
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
    @Column(name = "charge_id_stp", nullable = false)
    private String chargeId;
    @Column(name = "status", nullable = false)
    private String status;
    @OneToOne()
    @JoinColumn(name = "order_id")
    private Order order;
}
