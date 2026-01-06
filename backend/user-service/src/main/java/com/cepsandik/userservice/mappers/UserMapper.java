package com.cepsandik.userservice.mappers;

import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.models.User;

public class UserMapper {
    public static UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.isVerified(),
                u.getProfileImage()
        );
    }
}
