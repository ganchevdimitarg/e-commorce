package com.concordeu.payment.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity(name = "Charges")
@Table(name = "charges")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AppCharge {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "charge_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "amount")
    private BigDecimal amount;
    @Column(name = "currency")
    private String currency;
    @Column(name = "customer_id_stp")
    private String customerId;
    @Column(name = "receipt_email")
    private String receiptEmail;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppCustomer customer;
}
