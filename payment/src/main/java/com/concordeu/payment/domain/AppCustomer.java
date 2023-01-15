package com.concordeu.payment.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.List;

@Entity(name = "Customers")
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "username", columnNames = "username")
        },
        indexes = @Index(name = "email_index", columnList = "username"))
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
    @Column(name = "customer_id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "username", unique = true)
    private String username;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "customer_id_stp")
    private String customerId;
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AppCharge> charges;
}
