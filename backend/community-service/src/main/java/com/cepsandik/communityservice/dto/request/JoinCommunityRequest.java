package com.cepsandik.communityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinCommunityRequest {

    @NotBlank(message = "Davet kodu zorunludur")
    @Size(min = 6, max = 10, message = "Geçersiz davet kodu")
    private String code;
}
