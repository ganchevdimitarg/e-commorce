package com.concordeu.order.domain;

import lombok.*;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

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
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @GeneratedValue
    @Column(name = "charge_id", length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "charge_id_stp", nullable = false)
    private String chargeId;
    @Column(name = "status", nullable = false)
    private String status;
    @CreationTimestamp
    @Column(name = "create_on",updatable = false)
    private OffsetDateTime createOn;
    @UpdateTimestamp
    @Column(name = "update_on")
    private OffsetDateTime updateOn;
    @OneToOne()
    @JoinColumn(name = "order_id")
    private Order order;
}
