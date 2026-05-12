package com.cepsandik.electionservice.dto.request;

import com.cepsandik.electionservice.enums.ElectionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateElectionRequest {

    @Size(min = 3, max = 200, message = "Başlık 3-200 karakter arasında olmalıdır")
    private String title;

    @Size(max = 2000, message = "Açıklama en fazla 2000 karakter olabilir")
    private String description;

    private ElectionType type;

    @Min(value = 1, message = "En az 1 seçim yapılabilmeli")
    private Integer maxSelections;

    @Future(message = "Başlangıç zamanı gelecekte olmalıdır")
    private Instant startTime;

    @Future(message = "Bitiş zamanı gelecekte olmalıdır")
    private Instant endTime;

    private Boolean resultsPublic;
}
