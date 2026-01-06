package com.cepsandik.userservice.dtos.responses;

/**
 * Response for 2FA setup containing QR code URI
 */
public record TwoFactorSetupResponse(
        String qrCodeUri,
        String secretKey,
        String[] backupCodes) {
}
