package com.ganchevdimitarg.notification.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity(name = "Notification")
@Table(name="notification" )
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "recipient", columnDefinition = "TEXT")
    @Email
    private String recipient;
    @Column(name = "subject", length = 200)
    private String subject;
    @Column(name = "msgBody", columnDefinition = "TEXT")
    @Size(min = 10, max = 251)
    private String msgBody;
    private String attachment;
    @Column(name = "created_on")
    LocalDateTime createdOn;
}
