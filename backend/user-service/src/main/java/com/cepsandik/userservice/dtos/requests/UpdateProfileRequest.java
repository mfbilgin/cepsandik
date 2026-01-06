package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "Ad en fazla 50 karakter olabilir") String firstName,

        @Size(max = 50, message = "Soyad en fazla 50 karakter olabilir") String lastName) {
}
