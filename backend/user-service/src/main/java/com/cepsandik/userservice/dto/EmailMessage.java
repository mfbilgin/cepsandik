package com.cepsandik.userservice.dto;

import java.io.Serializable;

/**
 * RabbitMQ üzerinden gönderilecek e-posta mesaj nesnesi.
 */
public record EmailMessage(
        String to,
        String firstName,
        String token,
        EmailType type) implements Serializable {

    public enum EmailType {
        VERIFICATION,
        PASSWORD_RESET
    }
}
