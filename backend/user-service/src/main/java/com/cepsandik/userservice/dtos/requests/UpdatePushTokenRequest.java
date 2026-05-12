package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePushTokenRequest {
    @NotBlank
    private String pushToken;
    
    private boolean isGuardianEligible;
}
