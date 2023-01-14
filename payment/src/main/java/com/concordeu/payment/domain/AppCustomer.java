package com.concordeu.payment.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.List;

@Entity(name = "Customers")
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "email", columnNames = "email")
        },
        indexes = @Index(name = "email_index", columnList = "email"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class AppCustomer {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "email", unique = true)
    private String email;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "customer_id")
    private String customerId;
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AppCharge> charges;
}
