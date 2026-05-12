package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mobile (E2E-V) client-side ElGamal şifrelemesi için gereken tüm
 * kriptografik parametreleri içerir. Bu endpoint'in cevabı önbelleklenebilir
 * (election ACTIVE olduktan sonra immutable). Mobile, manifestHash'i
 * sha256(electionManifest) ile lokal olarak doğrulayarak ortadaki adam
 * tampering'ini engellemelidir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncryptionParamsResponse {

    private Long electionId;

    /** ElectionGuard manifest spec sürümü (server ile mobile arasında match olmalı). */
    private String specVersion;

    /** ElectionGuard CiphertextElectionContext (JSON). manifest_hash + crypto_extended_base_hash içerir. */
    private String electionGuardContext;

    /** ElectionGuard InternalManifest (JSON). */
    private String electionManifest;

    /** ElGamal joint public key (hex ElementModP). N guardian'ın ortak ürettiği. */
    private String jointPublicKey;
}
