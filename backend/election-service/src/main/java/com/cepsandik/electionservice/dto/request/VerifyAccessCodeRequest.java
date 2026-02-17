package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAccessCodeRequest {

    @NotBlank(message = "Erişim kodu zorunludur")
    @Size(min = 6, max = 6, message = "Erişim kodu 6 karakter olmalıdır")
    private String code;
}
