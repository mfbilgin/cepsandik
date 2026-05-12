package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuardianDutyResponse {
    private Long electionId;
    private String electionTitle;
    private String communityName;
    private String status; // KEY_UPLOADED, etc.
    private ElectionStatus electionStatus;
    private String encryptedTallyChallenge;
}
