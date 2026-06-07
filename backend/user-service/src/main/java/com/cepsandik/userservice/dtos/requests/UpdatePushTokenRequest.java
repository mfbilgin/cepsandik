package com.cepsandik.userservice.dtos.requests;

import lombok.Data;

@Data
public class UpdatePushTokenRequest {
    private String pushToken;

    private boolean isGuardianEligible;
}
