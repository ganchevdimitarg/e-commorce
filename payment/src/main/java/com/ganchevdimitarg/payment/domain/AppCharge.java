package com.ganchevdimitarg.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "Charges")
@Table(
        name = "charges",
        uniqueConstraints = {
                @UniqueConstraint(name = "charge_id_stp", columnNames = "charge_id_stp")
        },
        indexes = @Index(name = "charge_id_stp_index", columnList = "charge_id_stp"))
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Setter
@Getter
public class AppCharge extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "charge_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "charge_id_stp")
    private String chargeId;
    @Column(name = "amount")
    private long amount;
    @Column(name = "currency")
    private String currency;
    @Column(name = "customer_id_stp")
    private String customerId;
    @Column(name = "receipt_email")
    private String receiptEmail;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", nullable = false)
    private AppCustomer customer;
}
