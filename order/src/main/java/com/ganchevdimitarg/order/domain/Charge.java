package com.ganchevdimitarg.order.domain;


import jakarta.persistence.*;
import lombok.*;

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
    @GeneratedValue(strategy = GenerationType.UUID)
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
