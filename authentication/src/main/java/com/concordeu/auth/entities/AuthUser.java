package com.concordeu.auth.entities;

import com.concordeu.client.common.ProfileGrantedAuthority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Set;

@Document("users")
@Data
@Builder
public class AuthUser {
    @Id
    private String id;
    @Indexed(unique = true)
    @Size(min = 5, max = 20, message = "Email must be between 5 and 20 characters!")
    @NotBlank(message = "Email can not be empty!")
    @Email
    private String username;
    @Size(min = 6, max = 12, message = "Password must be between 6 and 12 characters!")
    @NotBlank(message = "Password can not be empty!")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,12}$",
            message = """
                    a digit must occur at least once
                    a lower case letter must occur at least once
                    an upper case letter must occur at least once
                    a special character must occur at least once
                    no whitespace allowed in the entire string
                    """)
    private String password;
    private Set<ProfileGrantedAuthority> grantedAuthorities;
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
    @Size(min = 9, max = 10, message = "Phone number must be between 9 and 10 characters!")
    @NotBlank(message = "Phone number can not be empty!")
    @Pattern(regexp = "^([0-9])*$",
            message = "Phone number must contain only digits!")
    private String phoneNumber;
    private OffsetDateTime created;
}
