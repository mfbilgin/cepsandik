package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDecryptionShareRequest {

    @NotBlank(message = "Deşifre payı boş olamaz")
    private String shareData;
}
