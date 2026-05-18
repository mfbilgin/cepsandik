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
    CANCELLED,

    /**
     * Faz 2.8 — Tally tekrar tekrar başarısız oldu (retry cap aşıldı).
     * Election CLOSED'tan bu duruma geçer; sessizce asılı kalmaz. Owner
     * inceleyip manuel müdahale eder (oylar + bulletin korunur).
     */
    TALLY_FAILED
}
