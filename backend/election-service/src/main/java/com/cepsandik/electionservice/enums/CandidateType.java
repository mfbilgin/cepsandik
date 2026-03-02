package com.cepsandik.electionservice.enums;

/**
 * Bir seçimdeki adayın türünü belirtir.
 * PERSON → Topluluk üyeleri arasından seçilen kişi (ad ve avatar otomatik
 * gelir)
 * TEXT_OPTION → Serbest metin seçeneği (evet/hayır, seçenek A/B vb.)
 * IMAGE_OPTION → Görselle temsil edilen seçenek (logo, grafik vb.)
 */
public enum CandidateType {
    PERSON,
    TEXT_OPTION,
    IMAGE_OPTION
}
