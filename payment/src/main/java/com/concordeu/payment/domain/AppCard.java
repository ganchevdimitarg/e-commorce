package com.concordeu.payment.domain;

import lombok.*;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

@Entity(name = "Cards")
@Table(
        name = "cards",
        uniqueConstraints = {
                @UniqueConstraint(name = "card_id_stp", columnNames = "card_id_stp")
        },
        indexes = @Index(name = "card_id_stp", columnList = "card_id_stp"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AppCard {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @GeneratedValue
    @Column(name = "card_id",length = 36, columnDefinition = "varchar(36)", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "card_id_stp", nullable = false)
    private String cardId;
    @Column(name = "brand", nullable = false)
    private String brand;
    @Column(name = "customer_id_stp", nullable = false)
    private String customerId;
    @Column(name = "card_Number")
    private String cardNumber;
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
