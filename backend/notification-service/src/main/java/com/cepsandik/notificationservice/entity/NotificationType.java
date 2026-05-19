package com.cepsandik.notificationservice.entity;

/**
 * Inbox'a persist edilen bildirim türleri. Şu an sadece sandık görevlisi
 * atamaları kalıcı tutuluyor — diğer event'ler (oluşturma, başlama, sonuç)
 * push/email ile anlık iletiliyor, inbox geçmişine işlenmiyor.
 */
public enum NotificationType {
    GUARDIAN_DUTY
}
