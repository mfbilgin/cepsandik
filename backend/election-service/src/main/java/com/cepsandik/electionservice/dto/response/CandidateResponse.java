package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.CandidateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateResponse {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Integer displayOrder;
    private CandidateType candidateType;
    private String memberUserId;
    private LocalDateTime createdAt;
}
