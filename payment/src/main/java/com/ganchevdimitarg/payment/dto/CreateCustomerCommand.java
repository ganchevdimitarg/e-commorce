package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for creating a customer. The username doubles as the provider email. */
public record CreateCustomerCommand(
        @NotBlank @Email String username) {
}
