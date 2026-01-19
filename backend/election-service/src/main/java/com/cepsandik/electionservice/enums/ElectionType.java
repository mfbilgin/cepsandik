package com.cepsandik.electionservice.enums;

/**
 * Seçim türleri
 */
public enum ElectionType {
    
    /** Tek seçimlik - sadece 1 aday seçilebilir */
    SINGLE_CHOICE,
    
    /** Çoklu seçim - birden fazla aday seçilebilir */
    MULTIPLE_CHOICE,
    
    /** Sıralama - adaylar tercih sırasına göre sıralanır */
    RANKED_CHOICE
}
