package com.concordeu.auth.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

@Document("users")
@Data
@Builder
public class AuthUser {
    @Id
    private String id;
    @NotBlank
    private String username;
    @Size(min = 6, max = 12, message = "Password must be between 6 and 12 characters!")
    @NotBlank(message = "Password can not be empty!")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,12}$",
            message = "a digit must occur at least once\n" +
                    "a lower case letter must occur at least once\n" +
                    "an upper case letter must occur at least once\n" +
                    "a special character must occur at least once\n" +
                    "no whitespace allowed in the entire string")
    private String password;
    private Set<? extends GrantedAuthority> grantedAuthorities;
    @Size(min = 3, max = 12, message = "First name must be between 3 and 12 characters!")
    @NotBlank(message = "First name can not be empty!")
    @Pattern(regexp = "^([A-Z])(\\p{L})(?=\\S+$).{3,12}$",
            message = "First name must contain uppercase first letter and then lowercase letters!")
    @Pattern(regexp = "\\D*", message = "First name cannot contain digit/digits!")
    private String firstName;
    @Size(min = 3, max = 12, message = "Last name must be between 3 and 12 characters!")
    @NotBlank(message = "Last name can not be empty!")
    @Pattern(regexp = "^([A-Z])(\\p{L})(?=\\S+$).{3,12}$",
            message = "Last name must contain uppercase first letter and then lowercase letters!")
    @Pattern(regexp = "\\D*", message = "Last name cannot contain digit/digits!")
    private String lastName;
    private Address address;
    @Indexed(unique = true)
    @Size(min = 5, max = 20, message = "Email must be between 5 and 20 characters!")
    @NotBlank(message = "Email can not be empty!")
    @Email
    private String email;
    @Size(min = 9, max = 10, message = "Phone number must be between 9 and 10 characters!")
    @NotBlank(message = "Phone number can not be empty!")
    @Pattern(regexp = "^([0-9])*$",
            message = "Phone number must contain only digits!")
    private String phoneNumber;
    private LocalDateTime created;
}
