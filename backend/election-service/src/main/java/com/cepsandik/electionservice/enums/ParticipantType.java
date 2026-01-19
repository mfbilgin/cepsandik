package com.cepsandik.electionservice.enums;

/**
 * Katılımcı belirleme yöntemi
 */
public enum ParticipantType {
    
    /** Tüm topluluk üyeleri */
    ALL_MEMBERS,
    
    /** Manuel seçilen üyeler */
    SELECTED_MEMBERS,
    
    /** Erişim kodu ile */
    ACCESS_CODE,
    
    /** Genel erişim (public seçim) */
    PUBLIC
}
