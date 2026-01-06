package com.cepsandik.communityservice.dto.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvitationRequest {

    @Min(value = 1, message = "Maksimum kullanım en az 1 olmalıdır")
    private Integer maxUses; // null = sınırsız

    @Min(value = 1, message = "Geçerlilik süresi en az 1 saat olmalıdır")
    private Integer expiresInHours; // null = süresiz
}
