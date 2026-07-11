package com.ganchevdimitarg.order.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity(name = "Charges")
@Table(
        name = "charges",
        uniqueConstraints = {
                @UniqueConstraint(name = "charge_id_stp", columnNames = "charge_id_stp")
        },
        indexes = @Index(name = "charge_id_stp_index", columnList = "charge_id_stp"))
@SQLDelete(sql = "UPDATE charges SET deleted_at = now() WHERE charge_id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Charge extends Auditable {
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
