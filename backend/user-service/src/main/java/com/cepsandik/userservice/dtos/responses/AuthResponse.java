package com.cepsandik.userservice.dtos.responses;

import lombok.Getter;

public record AuthResponse(
        @Getter String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpireDate,
        boolean requires2FA,
        String tempToken) {
    public static AuthResponse bearer(String access, String refresh, long accessTokenExpireDate) {
        return new AuthResponse(access, refresh, "Bearer", accessTokenExpireDate, false, null);
    }

    /**
     * 2FA gerektiren login için geçici token döner
     */
    public static AuthResponse requires2FA(String tempToken) {
        return new AuthResponse(null, null, null, 0, true, tempToken);
    }
}
