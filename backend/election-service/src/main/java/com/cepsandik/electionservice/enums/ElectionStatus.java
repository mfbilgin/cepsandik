package com.cepsandik.electionservice.enums;

/**
 * Seçim durumları state machine
 * 
 * DRAFT → SCHEDULED → ACTIVE → CLOSED → ARCHIVED
 *   │         │          │        │
 *   └─────────┴──────────┴────────┴──→ CANCELLED
 */
public enum ElectionStatus {
    
    /** Taslak - henüz yayınlanmadı, düzenlenebilir */
    DRAFT,
    
    /** Planlandı - yayınlandı, başlangıç bekleniyor */
    SCHEDULED,
    
    /** Aktif - oylama devam ediyor */
    ACTIVE,
    
    /** Kapandı - oylama bitti, sonuçlar hesaplanıyor */
    CLOSED,
    
    /** Arşivlendi - sonuçlar yayınlandı */
    ARCHIVED,
    
    /** İptal edildi */
    CANCELLED
}
