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

@Entity(name = "Cards")
@Table(
        name = "cards",
        uniqueConstraints = {
                @UniqueConstraint(name = "card_id_stp", columnNames = "card_id_stp")
        },
        indexes = @Index(name = "card_id_stp", columnList = "card_id_stp"))
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Setter
@Getter
public class AppCard extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "card_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "card_id_stp", nullable = false)
    private String cardId;
    @Column(name = "brand", nullable = false)
    private String brand;
    @Column(name = "customer_id_stp", nullable = false)
    private String customerId;
    @Column(name = "cvc_check", nullable = false)
    private String cvcCheck;
    @Column(name = "exp_month", nullable = false)
    private long expMonth;
    @Column(name = "exp_year", nullable = false)
    private long expYear;
    @Column(name = "last_four_digits", nullable = false)
    private String lastFourDigits;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id",nullable = false)
    private AppCustomer customer;
}
