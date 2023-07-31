package com.concordeu.payment.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.util.List;
import java.util.Set;

@Entity(name = "Customers")
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "customer_id_stp", columnNames = "customer_id_stp")
        },
        indexes = @Index(name = "customer_id_stp", columnList = "customer_id_stp"))
@NamedEntityGraph(
        name = "graph-payment-customer",
        attributeNodes = {
                @NamedAttributeNode(value = "charges", subgraph = "payment-charges"),
                @NamedAttributeNode(value = "cards", subgraph = "payment-cards")
        },
        subgraphs = {
                @NamedSubgraph(name = "payment-charges", attributeNodes = @NamedAttributeNode("customer")),
                @NamedSubgraph(name = "payment-cards", attributeNodes = @NamedAttributeNode("customer"))
        })
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
    private Set<AppCharge> charges;
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AppCard> cards;
}
