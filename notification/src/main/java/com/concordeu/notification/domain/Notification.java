package com.concordeu.notification.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Entity(name = "Notifications")
@Table(name="notifications" )
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Notification {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
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
    OffsetDateTime createdOn;
}
