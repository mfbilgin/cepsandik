package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCandidateRequest {

    @NotBlank(message = "Aday adı zorunludur")
    @Size(min = 1, max = 200, message = "Aday adı 1-200 karakter arasında olmalıdır")
    private String name;

    @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir")
    private String description;

    private String imageUrl;

    private Integer displayOrder;
}
