package com.cepsandik.userservice.dtos.responses;

import lombok.Getter;

import java.util.UUID;

public record UserResponse(
        @Getter
        UUID id,
        String firstName,
        String lastName,
        String email,
        boolean verified,
        String profileImage
) {
}
