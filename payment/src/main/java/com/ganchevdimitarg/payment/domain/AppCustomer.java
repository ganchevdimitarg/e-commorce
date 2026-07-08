package com.ganchevdimitarg.payment.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity(name = "Customers")
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "customer_id_stp", columnNames = "customer_id_stp")
        },
        indexes = @Index(name = "customer_id_stp", columnList = "customer_id_stp"))
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Setter
@Getter
public class AppCustomer extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AppCard> cards;
}
