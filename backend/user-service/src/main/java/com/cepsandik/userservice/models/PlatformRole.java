package com.cepsandik.userservice.models;

/**
 * Platform genelinde kullanıcı rolleri.
 * Topluluk ve seçim rollerinden bağımsızdır.
 */
public enum PlatformRole {
    /**
     * Normal kullanıcı - varsayılan rol
     */
    USER,

    /**
     * Moderatör - içerik denetimi yapabilir
     */
    MODERATOR,

    /**
     * Platform yöneticisi - tam yetki
     */
    ADMIN
}
