package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessCodeResponse {

    private Long id;
    private String code;
    private Integer maxUses;
    private Integer currentUses;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private String createdBy;
    private LocalDateTime createdAt;
}
