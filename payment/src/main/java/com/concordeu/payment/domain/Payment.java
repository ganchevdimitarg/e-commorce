package com.concordeu.payment.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity(name = "Payments")
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "card_number", columnNames = "card_number")
        },
        indexes = @Index(name = "card_number_index", columnList = "card_number"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Payment {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "card_number", unique = true, nullable = false)
    @Size(min = 16, max = 16)
    private String cardNumber;
    @Column(name = "card_name", nullable = false)
    private String cardName;
    @Column(name = "card_cvc", nullable = false)
    @Size(min = 3, max = 3)
    private String cardCvc;
    @Column(name = "card_month", nullable = false)
    @Size(min = 2, max = 2)
    private String cardMonth;
    @Size(min = 4, max = 4)
    @Column(name = "card_year", nullable = false)
    private String cardYear;
}
