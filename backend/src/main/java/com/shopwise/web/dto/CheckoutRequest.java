package com.shopwise.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "full name is required")
        @Size(max = 160, message = "full name is too long")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "address line 1 is required")
        @Size(max = 200)
        String line1,

        @Size(max = 200)
        String line2,

        @NotBlank(message = "city is required")
        @Size(max = 120)
        String city,

        @NotBlank(message = "postcode is required")
        @Size(max = 32)
        String postcode,

        @NotBlank(message = "country is required")
        @Size(max = 80)
        String country,

        @Size(max = 400, message = "note is too long")
        String note) {
}