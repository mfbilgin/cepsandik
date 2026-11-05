package com.cepsandik.userservice.dtos.responses;

/**
 * enabled=false ise istemci bütünlük kontrolünü atlar (yerel/UAT ortamı).
 */
public record IntegrityChallengeResponse(boolean enabled, String nonce, long expiresInSeconds) {
}
