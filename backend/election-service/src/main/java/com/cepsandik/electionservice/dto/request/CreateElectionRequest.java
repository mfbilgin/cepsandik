package com.cepsandik.electionservice.dto.request;

import com.cepsandik.electionservice.enums.ElectionType;
import com.cepsandik.electionservice.enums.ParticipantType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateElectionRequest {

    @NotBlank(message = "Seçim başlığı zorunludur")
    @Size(min = 3, max = 200, message = "Başlık 3-200 karakter arasında olmalıdır")
    private String title;

    @Size(max = 2000, message = "Açıklama en fazla 2000 karakter olabilir")
    private String description;

    /** Topluluk ID (null ise bağımsız seçim) */
    private Long communityId;

    @NotNull(message = "Seçim türü zorunludur")
    private ElectionType type;

    @NotNull(message = "Katılımcı türü zorunludur")
    private ParticipantType participantType;

    /** Çoklu seçimde maksimum seçilebilecek aday sayısı */
    @Min(value = 1, message = "En az 1 seçim yapılabilmeli")
    private Integer maxSelections;

    @NotNull(message = "Başlangıç zamanı zorunludur")
    @Future(message = "Başlangıç zamanı gelecekte olmalıdır")
    private LocalDateTime startTime;

    @NotNull(message = "Bitiş zamanı zorunludur")
    @Future(message = "Bitiş zamanı gelecekte olmalıdır")
    private LocalDateTime endTime;

    /** Sonuçların herkese açık olup olmadığı */
    private Boolean resultsPublic = false;

    /** Anonim oylama */
    private Boolean anonymousVoting = true;
}
