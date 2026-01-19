package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccessCodeRequest {

    /** Maksimum kullanım sayısı (null = sınırsız) */
    @Min(value = 1, message = "En az 1 kullanım olmalı")
    private Integer maxUses;

    /** Geçerlilik süresi saat cinsinden (null = süresiz) */
    @Min(value = 1, message = "En az 1 saat geçerli olmalı")
    private Integer expiresInHours;
}
